package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidAmountException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountTest {

    @Test
    fun `should create account with valid data`() {
        // given & when
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // then
        assertThat(account.ownerName).isEqualTo("John Doe")
        assertThat(account.balance).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(account.version).isEqualTo(0)
    }

    @Test
    fun `should throw exception when owner name is blank`() {
        assertThatThrownBy {
            Account(ownerName = "", balance = BigDecimal.ZERO)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Owner name must not be blank")
    }

    @Test
    fun `should throw exception when balance is negative`() {
        assertThatThrownBy {
            Account(ownerName = "John", balance = BigDecimal("-100"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Balance must not be negative")
    }

    @Test
    fun `should deposit successfully and return new account`() {
        // given
        val account = Account(
            id = 1L,
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when
        val updatedAccount = account.deposit(BigDecimal("500.00"))

        // then
        assertThat(updatedAccount.balance).isEqualByComparingTo(BigDecimal("1500.00"))
        assertThat(updatedAccount.id).isEqualTo(1L)
        assertThat(updatedAccount.ownerName).isEqualTo("John Doe")

        // Original account should not be modified (immutability)
        assertThat(account.balance).isEqualByComparingTo(BigDecimal("1000.00"))
    }

    @Test
    fun `should throw exception when depositing zero or negative amount`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when & then
        assertThatThrownBy {
            account.deposit(BigDecimal.ZERO)
        }.isInstanceOf(InvalidAmountException::class.java)
            .hasMessageContaining("Amount must be positive")

        assertThatThrownBy {
            account.deposit(BigDecimal("-100"))
        }.isInstanceOf(InvalidAmountException::class.java)
    }

    @Test
    fun `should throw exception when depositing to non-active account`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.SUSPENDED
        )

        // when & then
        assertThatThrownBy {
            account.deposit(BigDecimal("100.00"))
        }.isInstanceOf(InvalidAccountStatusException::class.java)
            .hasMessageContaining("Account is not active")
    }

    @Test
    fun `should withdraw successfully and return new account`() {
        // given
        val account = Account(
            id = 1L,
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when
        val updatedAccount = account.withdraw(BigDecimal("300.00"))

        // then
        assertThat(updatedAccount.balance).isEqualByComparingTo(BigDecimal("700.00"))
        assertThat(updatedAccount.id).isEqualTo(1L)

        // Original account should not be modified (immutability)
        assertThat(account.balance).isEqualByComparingTo(BigDecimal("1000.00"))
    }

    @Test
    fun `should throw exception when withdrawing more than balance`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when & then
        assertThatThrownBy {
            account.withdraw(BigDecimal("1500.00"))
        }.isInstanceOf(InsufficientBalanceException::class.java)
            .hasMessageContaining("Insufficient balance")
            .hasMessageContaining("Current: 1000")
            .hasMessageContaining("Requested: 1500")
    }

    @Test
    fun `should throw exception when withdrawing zero or negative amount`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when & then
        assertThatThrownBy {
            account.withdraw(BigDecimal.ZERO)
        }.isInstanceOf(InvalidAmountException::class.java)

        assertThatThrownBy {
            account.withdraw(BigDecimal("-100"))
        }.isInstanceOf(InvalidAmountException::class.java)
    }

    @Test
    fun `should throw exception when withdrawing from non-active account`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.CLOSED
        )

        // when & then
        assertThatThrownBy {
            account.withdraw(BigDecimal("100.00"))
        }.isInstanceOf(InvalidAccountStatusException::class.java)
            .hasMessageContaining("Account is not active")
    }

    @Test
    fun `should allow withdrawing exact balance`() {
        // given
        val account = Account(
            ownerName = "John Doe",
            balance = BigDecimal("1000.00")
        )

        // when
        val updatedAccount = account.withdraw(BigDecimal("1000.00"))

        // then
        assertThat(updatedAccount.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
