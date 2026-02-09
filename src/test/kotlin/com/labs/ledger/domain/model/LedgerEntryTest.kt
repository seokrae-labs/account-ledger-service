package com.labs.ledger.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LedgerEntryTest {

    @Test
    fun `should create ledger entry with valid data`() {
        // given & when
        val entry = LedgerEntry(
            accountId = 1L,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("100.00"),
            referenceId = "REF-001",
            description = "Initial deposit"
        )

        // then
        assertThat(entry.accountId).isEqualTo(1L)
        assertThat(entry.type).isEqualTo(LedgerEntryType.CREDIT)
        assertThat(entry.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(entry.referenceId).isEqualTo("REF-001")
        assertThat(entry.description).isEqualTo("Initial deposit")
        assertThat(entry.createdAt).isNotNull()
    }

    @Test
    fun `should create ledger entry without optional fields`() {
        // given & when
        val entry = LedgerEntry(
            accountId = 1L,
            type = LedgerEntryType.DEBIT,
            amount = BigDecimal("50.00")
        )

        // then
        assertThat(entry.referenceId).isNull()
        assertThat(entry.description).isNull()
    }

    @Test
    fun `should throw exception when amount is zero`() {
        assertThatThrownBy {
            LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal.ZERO
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should throw exception when amount is negative`() {
        assertThatThrownBy {
            LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("-100.00")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should create DEBIT entry for withdrawal`() {
        // given & when
        val entry = LedgerEntry(
            accountId = 1L,
            type = LedgerEntryType.DEBIT,
            amount = BigDecimal("200.00"),
            description = "Withdrawal"
        )

        // then
        assertThat(entry.type).isEqualTo(LedgerEntryType.DEBIT)
        assertThat(entry.amount).isEqualByComparingTo(BigDecimal("200.00"))
    }

    @Test
    fun `should create CREDIT entry for deposit`() {
        // given & when
        val entry = LedgerEntry(
            accountId = 1L,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("300.00"),
            description = "Deposit"
        )

        // then
        assertThat(entry.type).isEqualTo(LedgerEntryType.CREDIT)
        assertThat(entry.amount).isEqualByComparingTo(BigDecimal("300.00"))
    }

    @Test
    fun `should be immutable - data class copy creates new instance`() {
        // given
        val original = LedgerEntry(
            id = 1L,
            accountId = 1L,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("100.00")
        )

        // when
        val copied = original.copy(amount = BigDecimal("200.00"))

        // then
        assertThat(original.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(copied.amount).isEqualByComparingTo(BigDecimal("200.00"))
        assertThat(original.id).isEqualTo(copied.id)
    }
}
