package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InvalidTransferStatusTransitionException
import java.math.BigDecimal
import java.time.LocalDateTime

data class Transfer(
    val id: Long? = null,
    val idempotencyKey: String,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: BigDecimal,
    val status: TransferStatus = TransferStatus.PENDING,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank" }
        require(fromAccountId != toAccountId) { "Cannot transfer to the same account" }
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
    }

    fun complete(): Transfer {
        if (status != TransferStatus.PENDING) {
            throw InvalidTransferStatusTransitionException(
                "Cannot complete transfer. Current status: $status"
            )
        }
        return copy(
            status = TransferStatus.COMPLETED,
            updatedAt = LocalDateTime.now()
        )
    }

    fun fail(): Transfer {
        if (status != TransferStatus.PENDING) {
            throw InvalidTransferStatusTransitionException(
                "Cannot fail transfer. Current status: $status"
            )
        }
        return copy(
            status = TransferStatus.FAILED,
            updatedAt = LocalDateTime.now()
        )
    }
}
