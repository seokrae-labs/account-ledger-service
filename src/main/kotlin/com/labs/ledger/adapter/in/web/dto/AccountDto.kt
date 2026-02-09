package com.labs.ledger.adapter.`in`.web.dto

import com.labs.ledger.domain.model.Account
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateAccountRequest(
    val ownerName: String
)

data class DepositRequest(
    val amount: BigDecimal,
    val description: String? = null
)

data class AccountResponse(
    val id: Long,
    val ownerName: String,
    val balance: BigDecimal,
    val status: String,
    val version: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(account: Account): AccountResponse {
            return AccountResponse(
                id = account.id!!,
                ownerName = account.ownerName,
                balance = account.balance,
                status = account.status.name,
                version = account.version,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt
            )
        }
    }
}
