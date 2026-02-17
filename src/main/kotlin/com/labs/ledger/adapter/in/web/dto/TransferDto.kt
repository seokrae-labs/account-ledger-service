package com.labs.ledger.adapter.`in`.web.dto

import com.labs.ledger.domain.model.Transfer
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransferRequest(
    @field:NotNull(message = "From account ID is required")
    val fromAccountId: Long?,
    @field:NotNull(message = "To account ID is required")
    val toAccountId: Long?,
    @field:Positive(message = "Amount must be positive")
    val amount: BigDecimal,
    val description: String? = null
)

data class TransferResponse(
    val id: Long,
    val idempotencyKey: String,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: BigDecimal,
    val status: String,
    val description: String?,
    val failureReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(transfer: Transfer): TransferResponse {
            return TransferResponse(
                id = transfer.id!!,
                idempotencyKey = transfer.idempotencyKey,
                fromAccountId = transfer.fromAccountId,
                toAccountId = transfer.toAccountId,
                amount = transfer.amount,
                status = transfer.status.name,
                description = transfer.description,
                failureReason = transfer.failureReason,
                createdAt = transfer.createdAt,
                updatedAt = transfer.updatedAt
            )
        }
    }
}
