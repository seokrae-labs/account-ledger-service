package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.DuplicateTransferException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Sql(
    scripts = ["/schema-reset.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class TransferServiceConcurrencyTest {

    @Autowired
    private lateinit var createAccountService: CreateAccountService

    @Autowired
    private lateinit var depositService: DepositService

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var getAccountBalanceService: GetAccountBalanceService

    private var account1Id: Long = 0
    private var account2Id: Long = 0

    @BeforeEach
    fun setup() {
        runBlocking {
            val account1 = createAccountService.execute("Account 1")
            val account2 = createAccountService.execute("Account 2")

            account1Id = account1.id!!
            account2Id = account2.id!!

            depositService.execute(account1Id, BigDecimal("10000.00"), null)
            depositService.execute(account2Id, BigDecimal("10000.00"), null)
        }
    }

    @Test
    fun `동시 이체 시 Deadlock 방지 검증`() = runBlocking {
        // given
        val transferAmount = BigDecimal("100.00")
        val concurrentTransfers = 5

        // when - A→B와 B→A 이체를 동시에 실행
        val transfers = (1..concurrentTransfers).flatMap { i ->
            listOf(
                async(Dispatchers.Default) {
                    try {
                        transferService.execute(
                            idempotencyKey = "transfer-a-to-b-$i",
                            fromAccountId = account1Id,
                            toAccountId = account2Id,
                            amount = transferAmount,
                            description = null
                        )
                        true
                    } catch (e: Exception) {
                        false
                    }
                },
                async(Dispatchers.Default) {
                    try {
                        transferService.execute(
                            idempotencyKey = "transfer-b-to-a-$i",
                            fromAccountId = account2Id,
                            toAccountId = account1Id,
                            amount = transferAmount,
                            description = null
                        )
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            )
        }.awaitAll()

        // then - 최종 잔액의 합은 변하지 않음
        val balance1 = getAccountBalanceService.execute(account1Id).balance
        val balance2 = getAccountBalanceService.execute(account2Id).balance
        val totalBalance = balance1 + balance2

        assert(totalBalance.compareTo(BigDecimal("20000.00")) == 0) {
            "Total balance should remain 20000.00, but got $totalBalance"
        }
    }

    @Test
    fun `동일 Idempotency-Key 동시 제출 시 중복 방지`() = runBlocking {
        // given
        val idempotencyKey = UUID.randomUUID().toString()

        // when - 첫 번째 이체
        val first = transferService.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = account1Id,
            toAccountId = account2Id,
            amount = BigDecimal("100.00"),
            description = null
        )

        // when - 동일한 key로 두 번째 요청 (순차적)
        val second = transferService.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = account1Id,
            toAccountId = account2Id,
            amount = BigDecimal("100.00"),
            description = null
        )

        // then - 두 요청이 동일한 Transfer를 반환 (Idempotency 보장)
        assert(first.id == second.id) {
            "Expected same transfer ID, but got first=${first.id}, second=${second.id}"
        }
        assert(first.status == second.status) {
            "Expected same status, but got first=${first.status}, second=${second.status}"
        }

        // 최종 잔액 확인 - 정확히 1번만 이체되어야 함
        val balance1 = getAccountBalanceService.execute(account1Id).balance
        assert(balance1.compareTo(BigDecimal("9900.00")) == 0) {
            "Expected 9900.00 (transferred once), but got $balance1"
        }
    }

    @Test
    fun `순차 이체 정확성 검증`() = runBlocking {
        // given
        val transferAmount = BigDecimal("10.00")
        val transferCount = 5

        // when - 순차적으로 이체 (R2DBC pool 제약 고려)
        repeat(transferCount) { i ->
            transferService.execute(
                idempotencyKey = "sequential-transfer-$i",
                fromAccountId = account1Id,
                toAccountId = account2Id,
                amount = transferAmount,
                description = null
            )
        }

        // then
        val balance1 = getAccountBalanceService.execute(account1Id).balance
        val balance2 = getAccountBalanceService.execute(account2Id).balance

        val expectedBalance1 = BigDecimal("10000.00") - (transferAmount * BigDecimal(transferCount))
        val expectedBalance2 = BigDecimal("10000.00") + (transferAmount * BigDecimal(transferCount))

        assert(balance1.compareTo(expectedBalance1) == 0) {
            "Account1 expected $expectedBalance1, got $balance1"
        }
        assert(balance2.compareTo(expectedBalance2) == 0) {
            "Account2 expected $expectedBalance2, got $balance2"
        }
    }
}
