package com.labs.ledger.application.service

import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class DepositServiceConcurrencyTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var createAccountService: CreateAccountService

    @Autowired
    private lateinit var depositService: DepositService

    @Autowired
    private lateinit var getAccountBalanceService: GetAccountBalanceService

    private var testAccountId: Long = 0

    @BeforeEach
    fun setup() = runBlocking {
        val account = createAccountService.execute("Concurrency Test User")
        testAccountId = account.id!!
    }

    @Test
    fun `동시 입금 시 Optimistic Locking 동작 검증`() = runBlocking {
        // given
        val concurrentRequests = 10
        val depositAmount = BigDecimal("100.00")

        // when - 10개의 동시 입금 요청
        val results = (1..concurrentRequests).map {
            async(Dispatchers.Default) {
                try {
                    depositService.execute(testAccountId, depositAmount, "Concurrent deposit $it")
                    true
                } catch (e: Exception) {
                    // Optimistic lock failures are expected
                    false
                }
            }
        }.awaitAll()

        // then - 최종 잔액 확인
        val finalAccount = getAccountBalanceService.execute(testAccountId)
        val expectedBalance = depositAmount * BigDecimal(concurrentRequests)

        assert(finalAccount.balance.compareTo(expectedBalance) == 0) {
            "Expected balance: $expectedBalance, but got: ${finalAccount.balance}"
        }

        // At least some requests should succeed (retry mechanism helps)
        val successCount = results.count { it }
        assert(successCount > 0) { "Expected at least some successful deposits, but got $successCount" }
    }

    @RepeatedTest(5)
    fun `동시 입금 반복 테스트 - Flaky Test 방지`() = runBlocking {
        // given
        val concurrentRequests = 5
        val depositAmount = BigDecimal("50.00")

        // when
        (1..concurrentRequests).map {
            async(Dispatchers.Default) {
                depositService.execute(testAccountId, depositAmount, "Repeated test $it")
            }
        }.awaitAll()

        // then
        val finalAccount = getAccountBalanceService.execute(testAccountId)
        val expectedBalance = depositAmount * BigDecimal(concurrentRequests)

        assert(finalAccount.balance.compareTo(expectedBalance) == 0) {
            "Repeated test failed: Expected $expectedBalance, got ${finalAccount.balance}"
        }
    }

    @Test
    fun `대량 동시 입금 테스트 - 20개 요청`() = runBlocking {
        // given
        val concurrentRequests = 20
        val depositAmount = BigDecimal("10.00")

        // when
        val startTime = System.currentTimeMillis()

        (1..concurrentRequests).map {
            async(Dispatchers.Default) {
                depositService.execute(testAccountId, depositAmount, null)
            }
        }.awaitAll()

        val duration = System.currentTimeMillis() - startTime

        // then
        val finalAccount = getAccountBalanceService.execute(testAccountId)
        val expectedBalance = depositAmount * BigDecimal(concurrentRequests)

        assert(finalAccount.balance.compareTo(expectedBalance) == 0) {
            "Expected balance: $expectedBalance, but got: ${finalAccount.balance}"
        }

        println("$concurrentRequests concurrent deposits completed in ${duration}ms")
    }

    @Test
    fun `순차 vs 병렬 입금 성능 비교`() = runBlocking {
        // Sequential deposits
        val sequentialStart = System.currentTimeMillis()
        repeat(10) {
            depositService.execute(testAccountId, BigDecimal("10.00"), null)
        }
        val sequentialDuration = System.currentTimeMillis() - sequentialStart

        // Create new account for parallel test
        val parallelAccount = createAccountService.execute("Parallel Test")
        val parallelAccountId = parallelAccount.id!!

        // Parallel deposits
        val parallelStart = System.currentTimeMillis()
        (1..10).map {
            async(Dispatchers.Default) {
                depositService.execute(parallelAccountId, BigDecimal("10.00"), null)
            }
        }.awaitAll()
        val parallelDuration = System.currentTimeMillis() - parallelStart

        println("Sequential: ${sequentialDuration}ms, Parallel: ${parallelDuration}ms")

        // Both should have same final balance
        val sequentialBalance = getAccountBalanceService.execute(testAccountId).balance
        val parallelBalance = getAccountBalanceService.execute(parallelAccountId).balance

        assert(sequentialBalance.compareTo(parallelBalance) == 0) {
            "Sequential and parallel results differ"
        }
    }

    @Test
    fun `여러 계좌에 대한 동시 입금`() = runBlocking {
        // given - 5개 계좌 생성
        val accounts = (1..5).map {
            createAccountService.execute("Multi Account $it")
        }

        // when - 각 계좌에 동시에 10번씩 입금
        accounts.flatMap { account ->
            (1..10).map {
                async(Dispatchers.Default) {
                    depositService.execute(account.id!!, BigDecimal("20.00"), null)
                }
            }
        }.awaitAll()

        // then - 각 계좌의 잔액 확인
        accounts.forEach { account ->
            val balance = getAccountBalanceService.execute(account.id!!).balance
            assert(balance.compareTo(BigDecimal("200.00")) == 0) {
                "Account ${account.id} expected 200.00, got $balance"
            }
        }
    }
}
