package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.DuplicateTransferException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
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
    fun setup() = runTest {
        val account1 = createAccountService.execute("Account 1")
        val account2 = createAccountService.execute("Account 2")

        account1Id = account1.id!!
        account2Id = account2.id!!

        depositService.execute(account1Id, BigDecimal("10000.00"), null)
        depositService.execute(account2Id, BigDecimal("10000.00"), null)
    }

    @Test
    fun `동시 이체 시 Deadlock 방지 검증`() = runTest {
        // given
        val transferAmount = BigDecimal("100.00")
        val concurrentTransfers = 10

        // when - A→B와 B→A 이체를 동시에 실행
        val transfers = (1..concurrentTransfers).flatMap { i ->
            listOf(
                async {
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
                async {
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

        assert(totalBalance == BigDecimal("20000.00")) {
            "Total balance should remain 20000.00, but got $totalBalance"
        }
    }

    @Test
    fun `동일 Idempotency-Key 동시 제출 시 중복 방지`() = runTest {
        // given
        val idempotencyKey = UUID.randomUUID().toString()
        val concurrentRequests = 10

        // when - 동일한 key로 10번 동시 요청
        val results = (1..concurrentRequests).map {
            async {
                try {
                    transferService.execute(
                        idempotencyKey = idempotencyKey,
                        fromAccountId = account1Id,
                        toAccountId = account2Id,
                        amount = BigDecimal("100.00"),
                        description = null
                    )
                    "success"
                } catch (e: DuplicateTransferException) {
                    "duplicate"
                } catch (e: Exception) {
                    "error: ${e.javaClass.simpleName}"
                }
            }
        }.awaitAll()

        // then - 정확히 하나만 성공
        val successCount = results.count { it == "success" }
        assert(successCount == 1) {
            "Expected exactly 1 success, but got $successCount"
        }

        // 최종 잔액 확인
        val balance1 = getAccountBalanceService.execute(account1Id).balance
        assert(balance1 == BigDecimal("9900.00")) {
            "Expected 9900.00, but got $balance1"
        }
    }

    @Test
    fun `대량 동시 이체 테스트`() = runTest {
        // given
        val transferAmount = BigDecimal("10.00")
        val concurrentTransfers = 30

        // when
        (1..concurrentTransfers).map { i ->
            async {
                transferService.execute(
                    idempotencyKey = "bulk-transfer-$i",
                    fromAccountId = account1Id,
                    toAccountId = account2Id,
                    amount = transferAmount,
                    description = null
                )
            }
        }.awaitAll()

        // then
        val balance1 = getAccountBalanceService.execute(account1Id).balance
        val balance2 = getAccountBalanceService.execute(account2Id).balance

        val expectedBalance1 = BigDecimal("10000.00") - (transferAmount * BigDecimal(concurrentTransfers))
        val expectedBalance2 = BigDecimal("10000.00") + (transferAmount * BigDecimal(concurrentTransfers))

        assert(balance1 == expectedBalance1) {
            "Account1 expected $expectedBalance1, got $balance1"
        }
        assert(balance2 == expectedBalance2) {
            "Account2 expected $expectedBalance2, got $balance2"
        }
    }
}
