package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidAmountException
import java.math.BigDecimal
import java.time.LocalDateTime

data class Account(
    val id: Long? = null,
    val ownerName: String,
    val balance: BigDecimal,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val version: Long = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(ownerName.isNotBlank()) { "Owner name must not be blank" }
        require(balance >= BigDecimal.ZERO) { "Balance must not be negative" }
    }

    fun deposit(amount: BigDecimal): Account {
        validateActive()
        validateAmountPositive(amount)

        return copy(
            balance = balance + amount,
            updatedAt = LocalDateTime.now()
        )
    }

    fun withdraw(amount: BigDecimal): Account {
        validateActive()
        validateAmountPositive(amount)

        if (balance < amount) {
            throw InsufficientBalanceException(
                "Insufficient balance. Current: $balance, Requested: $amount"
            )
        }

        return copy(
            balance = balance - amount,
            updatedAt = LocalDateTime.now()
        )
    }

    private fun validateActive() {
        if (status != AccountStatus.ACTIVE) {
            throw InvalidAccountStatusException("Account is not active: $status")
        }
    }

    private fun validateAmountPositive(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw InvalidAmountException("Amount must be positive: $amount")
        }
    }
}
