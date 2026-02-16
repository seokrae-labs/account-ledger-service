package com.labs.ledger.domain.model

import java.math.BigDecimal

/**
 * Command object for transfer execution
 *
 * Encapsulates all parameters needed to execute a transfer.
 * Used by TransferExecutionStrategy for strategy pattern implementation.
 *
 * @property idempotencyKey Unique key for idempotent processing
 * @property fromAccountId Source account ID
 * @property toAccountId Destination account ID
 * @property amount Transfer amount (must be positive)
 * @property description Optional transfer description
 */
data class TransferCommand(
    val idempotencyKey: String,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: BigDecimal,
    val description: String? = null
) {
    init {
        require(idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }
        require(fromAccountId > 0) { "From account ID must be positive" }
        require(toAccountId > 0) { "To account ID must be positive" }
        require(fromAccountId != toAccountId) { "Cannot transfer to same account" }
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
    }
}
