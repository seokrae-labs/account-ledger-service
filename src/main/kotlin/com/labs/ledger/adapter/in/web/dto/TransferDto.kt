package com.labs.ledger.adapter.`in`.web.dto

import com.labs.ledger.domain.model.Transfer
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransferRequest(
    val fromAccountId: Long,
    val toAccountId: Long,
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
                createdAt = transfer.createdAt,
                updatedAt = transfer.updatedAt
            )
        }
    }
}
