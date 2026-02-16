package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.DomainException
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidAmountException
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferAuditEvent
import com.labs.ledger.domain.model.TransferAuditEventType
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.TransferAuditRepository
import com.labs.ledger.domain.port.TransferRepository
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.application.support.retryOnOptimisticLock
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class TransferService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val transferRepository: TransferRepository,
    private val transactionExecutor: TransactionExecutor,
    private val transferAuditRepository: TransferAuditRepository
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
                    logger.warn { "Transfer previously failed: key=$idempotencyKey" }
                    return existingTransfer
                }
                TransferStatus.PENDING -> {
                    throw DuplicateTransferException(
                        "Transfer with idempotency key already exists in status: PENDING"
                    )
                }
            }
        }

        return retryOnOptimisticLock retry@{
            try {
                transactionExecutor.execute tx@{
                    // Double-check idempotency inside transaction (race condition protection)
                    val existingInTx = transferRepository.findByIdempotencyKey(idempotencyKey)
                    if (existingInTx != null) {
                        when (existingInTx.status) {
                            TransferStatus.COMPLETED -> {
                                return@tx existingInTx
                            }
                            TransferStatus.FAILED -> {
                                return@tx existingInTx
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

                    // Record successful audit event (in same transaction)
                    transferAuditRepository.save(
                        TransferAuditEvent(
                            transferId = saved.id,
                            idempotencyKey = idempotencyKey,
                            eventType = TransferAuditEventType.TRANSFER_COMPLETED,
                            transferStatus = TransferStatus.COMPLETED
                        )
                    )

                    logger.info { "Transfer completed: id=${saved.id}, from=$fromAccountId, to=$toAccountId, amount=$amount" }
                    saved
                }
            } catch (e: DomainException) {
                // Handle business failures outside main transaction
                when (e) {
                    is InsufficientBalanceException,
                    is InvalidAmountException,
                    is InvalidAccountStatusException,
                    is AccountNotFoundException -> {
                        persistFailureAndAudit(idempotencyKey, fromAccountId, toAccountId, amount, description, e)
                        throw e  // Maintain API contract
                    }
                    else -> {
                        // Other domain exceptions (e.g., DuplicateTransferException) should not save FAILED state
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Persist transfer failure state and audit event in independent transaction
     *
     * Design:
     * - Runs in separate transaction (survives main tx rollback)
     * - Upsert logic: update PENDING -> FAILED, or create new FAILED if PENDING was rolled back
     * - Records audit event in same transaction (atomic failure persistence)
     * - Logs but doesn't re-throw persistence errors (business exception takes precedence)
     *
     * @param idempotencyKey Transfer idempotency key
     * @param fromAccountId Source account ID
     * @param toAccountId Destination account ID
     * @param amount Transfer amount
     * @param description Transfer description
     * @param cause Business exception that caused the failure
     */
    private suspend fun persistFailureAndAudit(
        idempotencyKey: String,
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        description: String?,
        cause: DomainException
    ) {
        try {
            transactionExecutor.execute {
                // Upsert FAILED state (handles main tx rollback)
                val existing = transferRepository.findByIdempotencyKey(idempotencyKey)
                val failedTransfer = when {
                    existing == null -> {
                        // PENDING was rolled back -> create new FAILED
                        val transfer = Transfer(
                            idempotencyKey = idempotencyKey,
                            fromAccountId = fromAccountId,
                            toAccountId = toAccountId,
                            amount = amount,
                            description = description
                        ).fail(cause.message ?: "Unknown error")
                        transferRepository.save(transfer)
                    }
                    existing.status == TransferStatus.PENDING -> {
                        // PENDING exists (edge case: different tx isolation) -> update to FAILED
                        transferRepository.save(existing.fail(cause.message ?: "Unknown error"))
                    }
                    else -> {
                        // COMPLETED/FAILED already -> no-op
                        existing
                    }
                }

                // Record audit event (same transaction)
                transferAuditRepository.save(
                    TransferAuditEvent(
                        transferId = failedTransfer.id,
                        idempotencyKey = idempotencyKey,
                        eventType = TransferAuditEventType.TRANSFER_FAILED_BUSINESS,
                        transferStatus = failedTransfer.status,
                        reasonCode = cause::class.simpleName,
                        reasonMessage = cause.message
                    )
                )

                logger.warn { "Transfer failure persisted: key=$idempotencyKey, reason=${cause.message}" }
            }
        } catch (persistError: Exception) {
            // Log but don't re-throw - business exception takes precedence
            logger.error(persistError) {
                "Failed to persist failure state: key=$idempotencyKey, error=${persistError.message}"
            }
        }
    }
}
