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

    /**
     * Suspend the account. Only ACTIVE accounts can be suspended.
     * SUSPENDED accounts cannot perform deposits or withdrawals.
     */
    fun suspend(): Account {
        if (status == AccountStatus.CLOSED) {
            throw InvalidAccountStatusException("Cannot suspend a closed account")
        }
        if (status == AccountStatus.SUSPENDED) {
            throw InvalidAccountStatusException("Account is already suspended")
        }

        return copy(
            status = AccountStatus.SUSPENDED,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * Activate (reactivate) the account. Only SUSPENDED accounts can be activated.
     */
    fun activate(): Account {
        if (status == AccountStatus.CLOSED) {
            throw InvalidAccountStatusException("Cannot activate a closed account")
        }
        if (status == AccountStatus.ACTIVE) {
            throw InvalidAccountStatusException("Account is already active")
        }

        return copy(
            status = AccountStatus.ACTIVE,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * Close the account permanently. ACTIVE or SUSPENDED accounts can be closed.
     * Once closed, the account cannot be reactivated.
     */
    fun close(): Account {
        if (status == AccountStatus.CLOSED) {
            throw InvalidAccountStatusException("Account is already closed")
        }

        return copy(
            status = AccountStatus.CLOSED,
            updatedAt = LocalDateTime.now()
        )
    }
}
