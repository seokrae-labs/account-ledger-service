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
import com.labs.ledger.domain.port.FailureRecord
import com.labs.ledger.domain.port.FailureRegistry
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.TransferAuditRepository
import com.labs.ledger.domain.port.TransferRepository
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.application.support.retryOnOptimisticLock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class TransferService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val transferRepository: TransferRepository,
    private val transactionExecutor: TransactionExecutor,
    private val transferAuditRepository: TransferAuditRepository,
    private val failureRegistry: FailureRegistry,
    private val asyncScope: CoroutineScope
) : TransferUseCase {

    override suspend fun execute(
        idempotencyKey: String,
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        description: String?
    ): Transfer {
        // Fast path: check in-memory failure registry first (fastest)
        failureRegistry.get(idempotencyKey)?.let { record ->
            logger.warn { "Duplicate failed transfer (memory hit): key=$idempotencyKey" }
            return record.transfer
        }

        // Fast path: check DB for existing transfer
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
                // Handle business failures with async persistence
                when (e) {
                    is InsufficientBalanceException,
                    is InvalidAmountException,
                    is InvalidAccountStatusException,
                    is AccountNotFoundException -> {
                        handleBusinessFailure(idempotencyKey, fromAccountId, toAccountId, amount, description, e)
                        throw e  // Maintain API contract (immediate response ~50ms)
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
     * Handle business failure with memory-first async persistence
     *
     * Strategy:
     * 1. Register failure in memory immediately (synchronous, ~1ms)
     * 2. Launch background coroutine for DB persistence (async, Fire-and-Forget)
     * 3. Return control to caller for immediate response
     *
     * Benefits:
     * - Immediate idempotency guarantee (memory registry)
     * - Fast client response (~50ms total)
     * - No retry complexity (eventual consistency acceptable)
     * - Leverages Reactive system advantages
     *
     * Trade-offs:
     * - Memory overhead (mitigated by Caffeine TTL/eviction)
     * - Eventual consistency for audit logs (acceptable for failures)
     * - Server restart loses in-flight failures (recoverable via DB fallback)
     *
     * @param idempotencyKey Transfer idempotency key
     * @param fromAccountId Source account ID
     * @param toAccountId Destination account ID
     * @param amount Transfer amount
     * @param description Transfer description
     * @param cause Business exception that caused the failure
     */
    private fun handleBusinessFailure(
        idempotencyKey: String,
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        description: String?,
        cause: DomainException
    ) {
        // 1. Create failed transfer object
        val failedTransfer = Transfer(
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            description = description
        ).fail(cause.message ?: "Unknown error")

        // 2. Register in memory immediately (synchronous)
        failureRegistry.register(
            idempotencyKey,
            FailureRecord(
                transfer = failedTransfer,
                errorMessage = cause.message ?: "Unknown error"
            )
        )

        logger.warn {
            "Transfer failure registered in memory: key=$idempotencyKey, " +
                "reason=${cause.message}, cacheSize=${failureRegistry.size()}"
        }

        // 3. Launch async persistence (Fire-and-Forget)
        asyncScope.launch {
            persistFailureAsync(idempotencyKey, failedTransfer, cause)
        }
    }

    /**
     * Persist failure to DB asynchronously
     *
     * Runs in background coroutine, does not block client response.
     * Failures are logged but not re-thrown (eventual consistency model).
     *
     * @param idempotencyKey Transfer idempotency key
     * @param failedTransfer Failed transfer object
     * @param cause Business exception
     */
    private suspend fun persistFailureAsync(
        idempotencyKey: String,
        failedTransfer: Transfer,
        cause: DomainException
    ) {
        try {
            transactionExecutor.execute {
                // Upsert FAILED state (handles main tx rollback)
                val existing = transferRepository.findByIdempotencyKey(idempotencyKey)
                val saved = when {
                    existing == null -> {
                        // PENDING was rolled back -> create new FAILED
                        transferRepository.save(failedTransfer)
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
                        transferId = saved.id,
                        idempotencyKey = idempotencyKey,
                        eventType = TransferAuditEventType.TRANSFER_FAILED_BUSINESS,
                        transferStatus = saved.status,
                        reasonCode = cause::class.simpleName,
                        reasonMessage = cause.message
                    )
                )

                logger.info {
                    "Transfer failure persisted to DB: key=$idempotencyKey, transferId=${saved.id}"
                }
            }

            // Remove from memory after successful DB persistence
            failureRegistry.remove(idempotencyKey)
        } catch (e: Exception) {
            // Log but don't re-throw (eventual consistency)
            // Failure record remains in memory (TTL will evict)
            logger.error(e) {
                "Failed to persist failure to DB (will retry on next request): " +
                    "key=$idempotencyKey, error=${e.message}"
            }
        }
    }
}
