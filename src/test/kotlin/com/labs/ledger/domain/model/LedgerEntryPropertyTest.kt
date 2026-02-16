package com.labs.ledger.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Property-based 테스트: LedgerEntry 도메인 모델의 불변량 검증
 *
 * 핵심 불변량:
 * - 금액은 항상 양수
 * - 불변 객체 (모든 필드 변경 불가)
 */
class LedgerEntryPropertyTest {

    @Test
    fun `0 또는 음수 금액 LedgerEntry는 생성할 수 없다`() = runTest {
        val nonPositiveAmounts = Arb.bigDecimal(
            min = BigDecimal("-1000000"),
            max = BigDecimal.ZERO
        )

        checkAll(1000, Arb.long(min = 1, max = 1000000), nonPositiveAmounts) { accountId, invalidAmount ->
            // when & then
            shouldThrow<IllegalArgumentException> {
                LedgerEntry(
                    accountId = accountId,
                    type = LedgerEntryType.CREDIT,
                    amount = invalidAmount
                )
            }
        }
    }

    @Test
    fun `양수 금액 LedgerEntry는 항상 생성 가능하다`() = runTest {
        checkAll(
            1000,
            Arb.long(min = 1, max = 1000000),
            Arb.enum<LedgerEntryType>(),
            arbPositiveBigDecimal()
        ) { accountId, type, amount ->
            // when
            val entry = LedgerEntry(
                accountId = accountId,
                type = type,
                amount = amount
            )

            // then
            entry.amount shouldBeGreaterThan BigDecimal.ZERO
            entry.accountId shouldBe accountId
            entry.type shouldBe type
        }
    }

    @Test
    fun `CREDIT 타입 LedgerEntry는 항상 양수 금액을 가진다`() = runTest {
        checkAll(100, Arb.long(min = 1, max = 1000000), arbPositiveBigDecimal()) { accountId, amount ->
            // when
            val entry = LedgerEntry(
                accountId = accountId,
                type = LedgerEntryType.CREDIT,
                amount = amount
            )

            // then
            entry.type shouldBe LedgerEntryType.CREDIT
            entry.amount shouldBeGreaterThan BigDecimal.ZERO
        }
    }

    @Test
    fun `DEBIT 타입 LedgerEntry는 항상 양수 금액을 가진다`() = runTest {
        checkAll(100, Arb.long(min = 1, max = 1000000), arbPositiveBigDecimal()) { accountId, amount ->
            // when
            val entry = LedgerEntry(
                accountId = accountId,
                type = LedgerEntryType.DEBIT,
                amount = amount
            )

            // then
            entry.type shouldBe LedgerEntryType.DEBIT
            entry.amount shouldBeGreaterThan BigDecimal.ZERO
        }
    }

    @Test
    fun `LedgerEntry는 불변 객체다 (copy 시 새 인스턴스 생성)`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { amount ->
            // given
            val original = LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = amount,
                description = "Original"
            )

            // when
            val copied = original.copy(description = "Modified")

            // then
            copied.accountId shouldBe original.accountId
            copied.type shouldBe original.type
            copied.amount shouldBe original.amount
            copied.description shouldBe "Modified"
            original.description shouldBe "Original"  // 원본 불변
        }
    }

    @Test
    fun `referenceId와 description은 선택적이다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { amount ->
            // when - referenceId, description 없이 생성
            val entryWithoutOptional = LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = amount
            )

            // then
            entryWithoutOptional.referenceId shouldBe null
            entryWithoutOptional.description shouldBe null
        }
    }

    @Test
    fun `referenceId와 description을 지정할 수 있다`() = runTest {
        val refIds = Arb.string(minSize = 1, maxSize = 50)
        val descriptions = Arb.string(minSize = 1, maxSize = 200)

        checkAll(
            100,
            Arb.long(min = 1, max = 1000000),
            arbPositiveBigDecimal(),
            refIds,
            descriptions
        ) { accountId, amount, refId, desc ->
            // when
            val entry = LedgerEntry(
                accountId = accountId,
                type = LedgerEntryType.DEBIT,
                amount = amount,
                referenceId = refId,
                description = desc
            )

            // then
            entry.referenceId shouldBe refId
            entry.description shouldBe desc
        }
    }

    @Test
    fun `동일한 금액과 타입도 서로 다른 LedgerEntry가 될 수 있다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { amount ->
            // when
            val entry1 = LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = amount
            )
            val entry2 = LedgerEntry(
                accountId = 1L,
                type = LedgerEntryType.CREDIT,
                amount = amount
            )

            // then - 값은 같지만 별개의 인스턴스
            entry1.amount shouldBe entry2.amount
            entry1.type shouldBe entry2.type
            entry1.accountId shouldBe entry2.accountId
            // (id는 null이므로 구분 불가, 실제로는 DB에서 서로 다른 id 할당됨)
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
}
