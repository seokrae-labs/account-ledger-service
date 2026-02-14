package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InvalidTransferStatusTransitionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class TransferTest {

    @Test
    fun `should create transfer with valid data`() {
        // given & when
        val transfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            description = "Payment"
        )

        // then
        assertThat(transfer.idempotencyKey).isNotBlank()
        assertThat(transfer.fromAccountId).isEqualTo(1L)
        assertThat(transfer.toAccountId).isEqualTo(2L)
        assertThat(transfer.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(transfer.status).isEqualTo(TransferStatus.PENDING)
        assertThat(transfer.description).isEqualTo("Payment")
    }

    @Test
    fun `should throw exception when idempotency key is blank`() {
        assertThatThrownBy {
            Transfer(
                idempotencyKey = "",
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal("100.00")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Idempotency key must not be blank")
    }

    @Test
    fun `should throw exception when transferring to same account`() {
        assertThatThrownBy {
            Transfer(
                idempotencyKey = UUID.randomUUID().toString(),
                fromAccountId = 1L,
                toAccountId = 1L,
                amount = BigDecimal("100.00")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot transfer to the same account")
    }

    @Test
    fun `should throw exception when amount is zero`() {
        assertThatThrownBy {
            Transfer(
                idempotencyKey = UUID.randomUUID().toString(),
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal.ZERO
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should throw exception when amount is negative`() {
        assertThatThrownBy {
            Transfer(
                idempotencyKey = UUID.randomUUID().toString(),
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal("-100.00")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should complete transfer successfully`() {
        // given
        val transfer = Transfer(
            id = 1L,
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING
        )

        // when
        val completed = transfer.complete()

        // then
        assertThat(completed.status).isEqualTo(TransferStatus.COMPLETED)
        assertThat(completed.id).isEqualTo(1L)

        // Original should not be modified (immutability)
        assertThat(transfer.status).isEqualTo(TransferStatus.PENDING)
    }

    @Test
    fun `should throw exception when completing non-pending transfer`() {
        // given - COMPLETED transfer
        val completedTransfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.COMPLETED
        )

        // when & then
        assertThatThrownBy {
            completedTransfer.complete()
        }.isInstanceOf(InvalidTransferStatusTransitionException::class.java)
            .hasMessageContaining("Cannot complete transfer")
            .hasMessageContaining("Current status: COMPLETED")

        // given - FAILED transfer
        val failedTransfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED
        )

        // when & then
        assertThatThrownBy {
            failedTransfer.complete()
        }.isInstanceOf(InvalidTransferStatusTransitionException::class.java)
            .hasMessageContaining("Current status: FAILED")
    }

    @Test
    fun `should fail transfer successfully`() {
        // given
        val transfer = Transfer(
            id = 1L,
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING
        )

        // when
        val failed = transfer.fail("Test failure reason")

        // then
        assertThat(failed.status).isEqualTo(TransferStatus.FAILED)
        assertThat(failed.failureReason).isEqualTo("Test failure reason")
        assertThat(failed.id).isEqualTo(1L)

        // Original should not be modified (immutability)
        assertThat(transfer.status).isEqualTo(TransferStatus.PENDING)
    }

    @Test
    fun `should throw exception when failing non-pending transfer`() {
        // given
        val completedTransfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.COMPLETED
        )

        // when & then
        assertThatThrownBy {
            completedTransfer.fail("Test reason")
        }.isInstanceOf(InvalidTransferStatusTransitionException::class.java)
            .hasMessageContaining("Cannot fail transfer")
            .hasMessageContaining("Current status: COMPLETED")
    }

    @Test
    fun `should enforce one-way state transition`() {
        // given
        val transfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00")
        )

        // when
        val completed = transfer.complete()

        // then - cannot go back to PENDING
        assertThat(completed.status).isEqualTo(TransferStatus.COMPLETED)

        // Trying to fail after complete should throw exception
        assertThatThrownBy {
            completed.fail("Test reason")
        }.isInstanceOf(InvalidTransferStatusTransitionException::class.java)
    }

    @Test
    fun `should throw exception when failure reason is blank`() {
        // given
        val transfer = Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING
        )

        // when & then
        assertThatThrownBy {
            transfer.fail("")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Failure reason must not be blank")
    }
}
