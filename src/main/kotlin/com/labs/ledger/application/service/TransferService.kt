package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.TransferRepository
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.infrastructure.util.retryOnOptimisticLock
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class TransferService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val transferRepository: TransferRepository,
    private val transactionExecutor: TransactionExecutor
) : TransferUseCase {

    override suspend fun execute(
        idempotencyKey: String,
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        description: String?
    ): Transfer {
        // Fast path: check idempotency outside transaction
        val existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey)
        if (existingTransfer != null) {
            if (existingTransfer.status == TransferStatus.COMPLETED) {
                logger.warn { "Duplicate transfer attempt (idempotent): key=$idempotencyKey" }
                return existingTransfer
            }
            throw DuplicateTransferException(
                "Transfer with idempotency key already exists in status: ${existingTransfer.status}"
            )
        }

        return retryOnOptimisticLock retry@{
            transactionExecutor.execute tx@{
                // Double-check idempotency inside transaction (race condition protection)
                val existingInTx = transferRepository.findByIdempotencyKey(idempotencyKey)
                if (existingInTx != null) {
                    if (existingInTx.status == TransferStatus.COMPLETED) {
                        return@tx existingInTx
                    }
                    throw DuplicateTransferException(
                        "Transfer with idempotency key already exists in status: ${existingInTx.status}"
                    )
                }

                // Create pending transfer
                val transfer = Transfer(
                    idempotencyKey = idempotencyKey,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = amount,
                    status = TransferStatus.PENDING,
                    description = description
                )
                val pendingTransfer = transferRepository.save(transfer)

                // Load accounts in sorted order (deadlock prevention)
                val sortedIds = listOf(fromAccountId, toAccountId).sorted()
                val accounts = accountRepository.findByIdsForUpdate(sortedIds)

                val fromAccount = accounts.find { it.id == fromAccountId }
                    ?: throw AccountNotFoundException("From account not found: $fromAccountId")
                val toAccount = accounts.find { it.id == toAccountId }
                    ?: throw AccountNotFoundException("To account not found: $toAccountId")

                // Execute domain logic
                val debitedAccount = fromAccount.withdraw(amount)
                val creditedAccount = toAccount.deposit(amount)

                // Update balances (optimistic lock will throw if concurrent modification)
                accountRepository.save(debitedAccount)
                accountRepository.save(creditedAccount)

                // Create ledger entries
                ledgerEntryRepository.saveAll(
                    listOf(
                        LedgerEntry(
                            accountId = fromAccountId,
                            type = LedgerEntryType.DEBIT,
                            amount = amount,
                            referenceId = idempotencyKey,
                            description = "Transfer to account $toAccountId"
                        ),
                        LedgerEntry(
                            accountId = toAccountId,
                            type = LedgerEntryType.CREDIT,
                            amount = amount,
                            referenceId = idempotencyKey,
                            description = "Transfer from account $fromAccountId"
                        )
                    )
                )

                // Complete transfer
                val completedTransfer = pendingTransfer.complete()
                val saved = transferRepository.save(completedTransfer)

                logger.info { "Transfer completed: id=${saved.id}, from=$fromAccountId, to=$toAccountId, amount=$amount" }
                saved
            }
        }
    }
}
