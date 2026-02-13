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
            when (existingTransfer.status) {
                TransferStatus.COMPLETED -> {
                    logger.warn { "Duplicate transfer attempt (idempotent): key=$idempotencyKey" }
                    return existingTransfer
                }
                TransferStatus.FAILED -> {
                    logger.warn { "Retry transfer after previous failure: key=$idempotencyKey" }
                    // Allow retry for failed transfers
                }
                TransferStatus.PENDING -> {
                    throw DuplicateTransferException(
                        "Transfer with idempotency key already exists in status: PENDING"
                    )
                }
            }
        }

        return retryOnOptimisticLock retry@{
            transactionExecutor.execute tx@{
                // Double-check idempotency inside transaction (race condition protection)
                val existingInTx = transferRepository.findByIdempotencyKey(idempotencyKey)
                if (existingInTx != null) {
                    when (existingInTx.status) {
                        TransferStatus.COMPLETED -> {
                            return@tx existingInTx
                        }
                        TransferStatus.FAILED -> {
                            // Allow retry for failed transfers
                            logger.info { "Retrying failed transfer in transaction: key=$idempotencyKey" }
                        }
                        TransferStatus.PENDING -> {
                            throw DuplicateTransferException(
                                "Transfer with idempotency key already exists in status: PENDING"
                            )
                        }
                    }
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
