package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InvalidTransferStatusTransitionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Property-based 테스트: Transfer 도메인 모델의 상태 머신 검증
 *
 * 핵심 불변량:
 * - PENDING → COMPLETED (성공)
 * - PENDING → FAILED (실패)
 * - COMPLETED/FAILED는 터미널 상태 (더 이상 변경 불가)
 */
class TransferPropertyTest {

    @Test
    fun `동일 계좌로 이체는 항상 거부된다`() = runTest {
        checkAll(1000, Arb.long(min = 1, max = 1000000)) { accountId ->
            // when & then
            shouldThrow<IllegalArgumentException> {
                Transfer(
                    idempotencyKey = UUID.randomUUID().toString(),
                    fromAccountId = accountId,
                    toAccountId = accountId,  // ← 동일 계좌
                    amount = BigDecimal("100.00")
                )
            }
        }
    }

    @Test
    fun `0 또는 음수 금액 이체는 항상 거부된다`() = runTest {
        val nonPositiveAmounts = Arb.bigDecimal(
            min = BigDecimal("-1000000"),
            max = BigDecimal.ZERO
        )

        checkAll(1000, nonPositiveAmounts) { invalidAmount ->
            // when & then
            shouldThrow<IllegalArgumentException> {
                Transfer(
                    idempotencyKey = UUID.randomUUID().toString(),
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amount = invalidAmount
                )
            }
        }
    }

    @Test
    fun `PENDING 상태의 이체만 완료할 수 있다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer()

            // when
            val completed = transfer.complete()

            // then
            completed.status shouldBe TransferStatus.COMPLETED
            completed.id shouldBe transfer.id
            completed.amount shouldBe transfer.amount
        }
    }

    @Test
    fun `COMPLETED 상태의 이체는 다시 완료할 수 없다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer().complete()

            // when & then
            shouldThrow<InvalidTransferStatusTransitionException> {
                transfer.complete()
            }
        }
    }

    @Test
    fun `FAILED 상태의 이체는 완료할 수 없다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer().fail("Test failure")

            // when & then
            shouldThrow<InvalidTransferStatusTransitionException> {
                transfer.complete()
            }
        }
    }

    @Test
    fun `PENDING 상태의 이체만 실패시킬 수 있다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer()
            val reason = "Test failure reason ${it + 1}"

            // when
            val failed = transfer.fail(reason)

            // then
            failed.status shouldBe TransferStatus.FAILED
            failed.failureReason shouldBe reason
            failed.id shouldBe transfer.id
        }
    }

    @Test
    fun `COMPLETED 상태의 이체는 실패시킬 수 없다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer().complete()

            // when & then
            shouldThrow<InvalidTransferStatusTransitionException> {
                transfer.fail("Cannot fail completed transfer")
            }
        }
    }

    @Test
    fun `FAILED 상태의 이체는 다시 실패시킬 수 없다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer().fail("First failure")

            // when & then
            shouldThrow<InvalidTransferStatusTransitionException> {
                transfer.fail("Second failure")
            }
        }
    }

    @Test
    fun `complete는 amount와 accountId를 변경하지 않는다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { amount ->
            // given
            val transfer = Transfer(
                idempotencyKey = UUID.randomUUID().toString(),
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = amount,
                status = TransferStatus.PENDING
            )

            // when
            val completed = transfer.complete()

            // then
            completed.amount shouldBe transfer.amount
            completed.fromAccountId shouldBe transfer.fromAccountId
            completed.toAccountId shouldBe transfer.toAccountId
            completed.idempotencyKey shouldBe transfer.idempotencyKey
        }
    }

    @Test
    fun `fail은 amount와 accountId를 변경하지 않는다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { amount ->
            // given
            val transfer = Transfer(
                idempotencyKey = UUID.randomUUID().toString(),
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = amount,
                status = TransferStatus.PENDING
            )

            // when
            val failed = transfer.fail("Insufficient balance")

            // then
            failed.amount shouldBe transfer.amount
            failed.fromAccountId shouldBe transfer.fromAccountId
            failed.toAccountId shouldBe transfer.toAccountId
            failed.idempotencyKey shouldBe transfer.idempotencyKey
        }
    }

    @Test
    fun `빈 문자열로 실패 사유를 지정할 수 없다`() = runTest {
        repeat(100) {
            // given
            val transfer = createPendingTransfer()

            // when & then
            shouldThrow<IllegalArgumentException> {
                transfer.fail("")
            }
        }
    }

    // ===== Arbitrary Generators =====

    /**
     * 양수 BigDecimal 생성 (0.01 ~ 10,000,000)
     */
    private fun arbPositiveBigDecimal(): Arb<BigDecimal> {
        return Arb.bigDecimal(
            min = BigDecimal("0.01"),
            max = BigDecimal("10000000")
        )
    }

    /**
     * 테스트용 PENDING 이체 생성
     */
    private fun createPendingTransfer(): Transfer {
        return Transfer(
            idempotencyKey = UUID.randomUUID().toString(),
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("500.00"),
            status = TransferStatus.PENDING
        )
    }
}
