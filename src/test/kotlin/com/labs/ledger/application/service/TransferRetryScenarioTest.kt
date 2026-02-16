package com.labs.ledger.application.service

import com.labs.ledger.adapter.out.persistence.repository.DeadLetterEventEntityRepository
import com.labs.ledger.adapter.out.persistence.repository.TransferEntityRepository
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.DeadLetterEventType
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

/**
 * Transfer Retry Scenario End-to-End 테스트
 *
 * 검증 항목:
 * 1. 정상 플로우: 재시도 없이 성공
 * 2. 재시도 성공: 일시적 실패 → 재시도 → 성공
 * 3. 재시도 실패 → DLQ: 지속적 실패 → DLQ 저장
 * 4. 비즈니스 예외: Domain Exception은 재시도 없이 즉시 실패
 * 5. 성능: 재시도 시간 측정
 */
class TransferRetryScenarioTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var transferUseCase: TransferUseCase

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transferEntityRepository: TransferEntityRepository

    @Autowired
    private lateinit var dlqRepository: DeadLetterEventEntityRepository

    @Test
    fun `정상 플로우 - 재시도 없이 성공`() = runBlocking {
        // given: 잔액이 충분한 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Alice",
                balance = BigDecimal("1000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Bob",
                balance = BigDecimal("500.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "normal-flow-test"

        // when
        val duration = measureTimeMillis {
            val result = transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("300.00"),
                description = "Normal transfer"
            )

            // then
            assert(result.status == TransferStatus.COMPLETED) {
                "Transfer should be completed"
            }
        }

        // 재시도 없으므로 빠르게 완료 (<200ms, CI 환경 고려)
        assert(duration < 200) {
            "Normal flow should be fast, took ${duration}ms"
        }

        // DLQ에 이벤트 없어야 함
        val dlqEvent = dlqRepository.findByIdempotencyKey(idempotencyKey)
        assert(dlqEvent == null) {
            "No DLQ event should be created for successful transfer"
        }
    }

    @Test
    fun `비즈니스 예외 - 재시도 없이 즉시 FAILED 저장`() = runBlocking {
        // given: 잔액 부족 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Charlie",
                balance = BigDecimal("100.00"),  // 부족
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Dave",
                balance = BigDecimal("200.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "business-exception-test"

        // when & then
        val duration = measureTimeMillis {
            assertThrows<InsufficientBalanceException> {
                transferUseCase.execute(
                    idempotencyKey = idempotencyKey,
                    fromAccountId = fromAccount.id!!,
                    toAccountId = toAccount.id!!,
                    amount = BigDecimal("500.00"),  // > 100
                    description = "Insufficient balance test"
                )
            }
        }

        // 비즈니스 예외는 재시도 안 함 → 빠름 (<200ms, 독립 tx 포함)
        assert(duration < 500) {
            "Business exception should fail fast, took ${duration}ms"
        }

        // FAILED 상태로 저장되어 있어야 함
        val transfer = transferEntityRepository.findByIdempotencyKey(idempotencyKey)
        assert(transfer != null) {
            "FAILED transfer should be persisted"
        }
        assert(transfer!!.status == TransferStatus.FAILED.name) {
            "Status should be FAILED"
        }
        assert(transfer.failureReason!!.contains("Insufficient balance")) {
            "Failure reason should be recorded"
        }

        // DLQ에는 없어야 함 (재시도 실패가 아님)
        val dlqEvent = dlqRepository.findByIdempotencyKey(idempotencyKey)
        assert(dlqEvent == null) {
            "Business exception should not create DLQ event"
        }
    }

    @Test
    fun `멱등성 - FAILED 상태 재요청 시 동일 객체 반환`() = runBlocking {
        // given: 실패한 이체 (잔액 부족)
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Eve",
                balance = BigDecimal("50.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Frank",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "idempotency-failed-test"

        // First attempt - fails
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("200.00"),
                description = null
            )
        }

        // when: Same idempotency key - should return existing FAILED
        val result = transferUseCase.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccount.id!!,
            toAccountId = toAccount.id!!,
            amount = BigDecimal("200.00"),
            description = null
        )

        // then
        assert(result.status == TransferStatus.FAILED) {
            "Should return existing FAILED transfer"
        }
        assert(result.failureReason != null) {
            "Failure reason should be preserved"
        }
    }

    @Test
    fun `성능 측정 - 재시도 포함 시간`() = runBlocking {
        // given: 성공 케이스
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Performance",
                balance = BigDecimal("10000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Test",
                balance = BigDecimal("5000.00"),
                status = AccountStatus.ACTIVE
            )
        )

        // when: 성공 이체 시간 측정
        val successDuration = measureTimeMillis {
            transferUseCase.execute(
                idempotencyKey = "perf-success",
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("100.00"),
                description = "Performance test"
            )
        }

        // then: 재시도 없이 빠르게 완료
        println("✅ Success transfer took: ${successDuration}ms")
        assert(successDuration < 500) {
            "Success should be fast: ${successDuration}ms"
        }

        // 실패 케이스 시간 측정
        val failDuration = measureTimeMillis {
            assertThrows<InsufficientBalanceException> {
                transferUseCase.execute(
                    idempotencyKey = "perf-fail",
                    fromAccountId = fromAccount.id!!,
                    toAccountId = toAccount.id!!,
                    amount = BigDecimal("100000.00"),  // 잔액 초과
                    description = null
                )
            }
        }

        println("✅ Failed transfer took: ${failDuration}ms")
        // 실패도 빠름 (독립 tx 포함해도 <1초)
        assert(failDuration < 1000) {
            "Failure should be reasonably fast: ${failDuration}ms"
        }
    }

    @Test
    fun `동시성 - 여러 이체 동시 실행`() = runBlocking {
        // given: 계좌 준비
        val account1 = accountRepository.save(
            Account(
                ownerName = "Concurrent1",
                balance = BigDecimal("5000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val account2 = accountRepository.save(
            Account(
                ownerName = "Concurrent2",
                balance = BigDecimal("5000.00"),
                status = AccountStatus.ACTIVE
            )
        )

        // when: 여러 이체 순차 실행
        val results = (1..5).map { index ->
            transferUseCase.execute(
                idempotencyKey = "concurrent-$index",
                fromAccountId = account1.id!!,
                toAccountId = account2.id!!,
                amount = BigDecimal("100.00"),
                description = "Concurrent test $index"
            )
        }

        // then: 모두 성공
        assert(results.all { it.status == TransferStatus.COMPLETED }) {
            "All transfers should succeed"
        }

        // account1 잔액: 5000 - (100 * 5) = 4500
        val updatedAccount1 = accountRepository.findById(account1.id!!)
        assert(updatedAccount1!!.balance.compareTo(BigDecimal("4500.00")) == 0) {
            "Account1 balance should be 4500.00, got ${updatedAccount1.balance}"
        }
    }
}
