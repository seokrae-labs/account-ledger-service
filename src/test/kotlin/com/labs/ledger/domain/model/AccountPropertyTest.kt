package com.labs.ledger.domain.model

import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidAmountException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Property-based 테스트: Account 도메인 모델의 불변량 검증
 *
 * 목적:
 * - 수백/수천 가지 랜덤 입력으로 불변량 자동 검증
 * - 엣지 케이스 자동 발견 (수동 테스트로 놓치기 쉬운 경우)
 * - 도메인 규칙이 항상 유지되는지 증명
 */
class AccountPropertyTest {

    @Test
    fun `입금 후 잔액은 항상 원래 잔액 + 입금액이다`() = runTest {
        checkAll(1000, arbPositiveBigDecimal(), arbPositiveBigDecimal()) { initialBalance, depositAmount ->
            // given
            val account = createActiveAccount(balance = initialBalance)

            // when
            val deposited = account.deposit(depositAmount)

            // then
            deposited.balance shouldBe (initialBalance + depositAmount)
        }
    }

    @Test
    fun `입금 후 잔액은 절대 음수가 될 수 없다`() = runTest {
        checkAll(1000, arbPositiveBigDecimal(), arbPositiveBigDecimal()) { initialBalance, depositAmount ->
            // given
            val account = createActiveAccount(balance = initialBalance)

            // when
            val deposited = account.deposit(depositAmount)

            // then
            deposited.balance shouldBeGreaterThanOrEqualTo BigDecimal.ZERO
        }
    }

    @Test
    fun `입금 후 출금하면 원래 잔액으로 돌아온다 (역연산)`() = runTest {
        checkAll(1000, arbPositiveBigDecimal(), arbPositiveBigDecimal()) { initialBalance, amount ->
            // given
            val account = createActiveAccount(balance = initialBalance)

            // when
            val afterDeposit = account.deposit(amount)
            val afterWithdraw = afterDeposit.withdraw(amount)

            // then
            afterWithdraw.balance.compareTo(initialBalance) shouldBe 0
        }
    }

    @Test
    fun `0 또는 음수 금액 입금은 항상 실패한다`() = runTest {
        checkAll(1000, arbNonPositiveBigDecimal()) { invalidAmount ->
            // given
            val account = createActiveAccount()

            // when & then
            shouldThrow<InvalidAmountException> {
                account.deposit(invalidAmount)
            }
        }
    }

    @Test
    fun `잔액보다 큰 금액 출금은 항상 실패한다`() = runTest {
        checkAll(1000, arbPositiveBigDecimal()) { balance ->
            // given
            val account = createActiveAccount(balance = balance)
            val excessAmount = balance + BigDecimal.ONE

            // when & then
            shouldThrow<InsufficientBalanceException> {
                account.withdraw(excessAmount)
            }
        }
    }

    @Test
    fun `입금은 id, ownerName, status를 변경하지 않는다`() = runTest {
        checkAll(100, arbPositiveBigDecimal(), arbPositiveBigDecimal()) { initialBalance, depositAmount ->
            // given
            val account = createActiveAccount(
                id = 123L,
                ownerName = "Alice",
                balance = initialBalance
            )

            // when
            val deposited = account.deposit(depositAmount)

            // then
            deposited.id shouldBe account.id
            deposited.ownerName shouldBe account.ownerName
            deposited.status shouldBe account.status
        }
    }

    @Test
    fun `출금은 id, ownerName, status를 변경하지 않는다`() = runTest {
        repeat(100) {
            // given
            val balance = BigDecimal("1000.00") // 충분한 잔액 보장
            val account = createActiveAccount(
                id = 456L,
                ownerName = "Bob",
                balance = balance
            )
            val withdrawAmount = BigDecimal("100.00") // 고정 출금 금액

            // when
            val withdrawn = account.withdraw(withdrawAmount)

            // then
            withdrawn.id shouldBe account.id
            withdrawn.ownerName shouldBe account.ownerName
            withdrawn.status shouldBe account.status
        }
    }

    @Test
    fun `SUSPENDED 계좌는 입금할 수 없다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { depositAmount ->
            // given
            val account = createActiveAccount().suspend()

            // when & then
            shouldThrow<com.labs.ledger.domain.exception.InvalidAccountStatusException> {
                account.deposit(depositAmount)
            }
        }
    }

    @Test
    fun `CLOSED 계좌는 입금할 수 없다`() = runTest {
        checkAll(100, arbPositiveBigDecimal()) { depositAmount ->
            // given
            val account = createActiveAccount().close()

            // when & then
            shouldThrow<com.labs.ledger.domain.exception.InvalidAccountStatusException> {
                account.deposit(depositAmount)
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
     * 0 또는 음수 BigDecimal 생성 (-1,000,000 ~ 0)
     */
    private fun arbNonPositiveBigDecimal(): Arb<BigDecimal> {
        return Arb.bigDecimal(
            min = BigDecimal("-1000000"),
            max = BigDecimal.ZERO
        )
    }

    /**
     * 테스트용 ACTIVE 계좌 생성
     */
    private fun createActiveAccount(
        id: Long? = null,
        ownerName: String = "TestUser",
        balance: BigDecimal = BigDecimal("1000.00")
    ): Account {
        return Account(
            id = id,
            ownerName = ownerName,
            balance = balance,
            status = AccountStatus.ACTIVE
        )
    }
}
