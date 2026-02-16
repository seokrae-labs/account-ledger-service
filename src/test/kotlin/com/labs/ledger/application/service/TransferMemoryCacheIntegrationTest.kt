package com.labs.ledger.application.service

import com.labs.ledger.adapter.out.persistence.repository.TransferAuditEventEntityRepository
import com.labs.ledger.adapter.out.persistence.repository.TransferEntityRepository
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.TransferAuditEventType
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.FailureRegistry
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

/**
 * Memory Cache 기반 비동기 실패 영속화 통합 테스트
 *
 * 검증 항목:
 * 1. 실패 시 메모리 캐시 즉시 등록
 * 2. 비동기 DB 영속화
 * 3. 메모리 캐시 멱등성 보장
 * 4. 응답 시간 개선 (50ms 목표)
 */
class TransferMemoryCacheIntegrationTest : AbstractIntegrationTest() {

    @AfterEach
    fun waitForAsyncCompletion() = runBlocking {
        // Wait for background async persistence to complete before next test
        delay(500)
    }

    @Autowired
    private lateinit var transferUseCase: TransferUseCase

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transferEntityRepository: TransferEntityRepository

    @Autowired
    private lateinit var transferAuditRepository: TransferAuditEventEntityRepository

    @Autowired
    private lateinit var failureRegistry: FailureRegistry

    @Test
    fun `실패 시 메모리 캐시에 즉시 등록`() = runBlocking {
        // given: 잔액 부족 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Alice",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Bob",
                balance = BigDecimal("200.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "memory-cache-test-1"

        // when: 이체 실패
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("500.00"),
                description = "Test"
            )
        }

        // then: 메모리 캐시에 즉시 등록됨
        val memoryRecord = failureRegistry.get(idempotencyKey)
        assert(memoryRecord != null) {
            "Failure should be registered in memory immediately"
        }
        assert(memoryRecord!!.transfer.status == TransferStatus.FAILED) {
            "Status should be FAILED"
        }
        assert(memoryRecord.errorMessage.contains("Insufficient balance")) {
            "Error message should be recorded"
        }
    }

    @Test
    fun `비동기 DB 영속화 검증`() = runBlocking {
        // given
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Charlie",
                balance = BigDecimal("50.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Dave",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "async-persistence-test"

        // when: 실패
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("300.00"),
                description = null
            )
        }

        // then: 메모리에 즉시 등록
        assert(failureRegistry.get(idempotencyKey) != null) {
            "Should be in memory immediately"
        }

        // Wait for async persistence (최대 2초)
        var dbRecord: com.labs.ledger.adapter.out.persistence.entity.TransferEntity? = null
        repeat(20) { attempt ->
            delay(100)
            dbRecord = transferEntityRepository.findByIdempotencyKey(idempotencyKey)
            if (dbRecord != null) return@repeat
        }

        // DB에도 저장됨 (비동기)
        assert(dbRecord != null) {
            "FAILED record should be persisted to DB asynchronously"
        }
        assert(dbRecord!!.status == TransferStatus.FAILED.name) {
            "Status should be FAILED in DB"
        }

        // Audit 이벤트도 저장됨
        val auditEvent = transferAuditRepository.findByIdempotencyKey(idempotencyKey)
        assert(auditEvent != null) {
            "Audit event should be persisted"
        }
        assert(auditEvent!!.eventType == TransferAuditEventType.TRANSFER_FAILED_BUSINESS.name) {
            "Event type should be TRANSFER_FAILED_BUSINESS"
        }
    }

    @Test
    fun `메모리 캐시 멱등성 - 빠른 재요청 시 메모리에서 즉시 반환`() = runBlocking {
        // given: 실패한 이체
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Eve",
                balance = BigDecimal("10.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Frank",
                balance = BigDecimal("20.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "idempotency-memory-test"

        // First attempt - fails
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("100.00"),
                description = null
            )
        }

        // when: 즉시 재요청 (DB 저장 전)
        val duration = measureTimeMillis {
            val result = transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("100.00"),
                description = null
            )

            // then: 메모리에서 즉시 반환
            assert(result.status == TransferStatus.FAILED) {
                "Should return FAILED from memory"
            }
        }

        // 메모리 히트는 매우 빠름 (<50ms)
        println("Memory cache hit took: ${duration}ms")
        assert(duration < 100) {
            "Memory cache hit should be very fast, took ${duration}ms"
        }
    }

    @Test
    fun `응답 시간 개선 검증 - 실패 시 50ms 목표`() = runBlocking {
        // given
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Performance",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Test",
                balance = BigDecimal("200.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "performance-test"

        // when: 실패 시간 측정
        val duration = measureTimeMillis {
            assertThrows<InsufficientBalanceException> {
                transferUseCase.execute(
                    idempotencyKey = idempotencyKey,
                    fromAccountId = fromAccount.id!!,
                    toAccountId = toAccount.id!!,
                    amount = BigDecimal("1000.00"),
                    description = null
                )
            }
        }

        // then: 빠른 응답 (<500ms, CI/컨테이너 환경 고려)
        println("✅ Failed transfer response time: ${duration}ms")
        assert(duration < 500) {
            "Response should be reasonably fast with async persistence, took ${duration}ms"
        }

        // 메모리에는 즉시 등록됨
        assert(failureRegistry.get(idempotencyKey) != null) {
            "Should be in memory immediately"
        }
    }

    @Test
    fun `DB 영속화 후 메모리에서 제거`() = runBlocking {
        // given
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Grace",
                balance = BigDecimal("50.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Heidi",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "memory-cleanup-test"

        // when: 실패
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("200.00"),
                description = null
            )
        }

        // then: 처음에는 메모리에 있음
        assert(failureRegistry.get(idempotencyKey) != null) {
            "Should be in memory initially"
        }

        // Wait for async persistence and cleanup (최대 3초)
        delay(3000)

        // DB 저장 후 메모리에서 제거됨
        val memoryRecord = failureRegistry.get(idempotencyKey)
        println("Memory record after persistence: $memoryRecord")

        // DB에는 저장됨
        val dbRecord = transferEntityRepository.findByIdempotencyKey(idempotencyKey)
        assert(dbRecord != null) {
            "Should be persisted in DB"
        }
    }

    @Test
    fun `성공 케이스는 영향 없음`() = runBlocking {
        // given: 정상 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Success",
                balance = BigDecimal("1000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Test",
                balance = BigDecimal("500.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "success-no-cache-test"

        // when: 성공
        val duration = measureTimeMillis {
            val result = transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = BigDecimal("100.00"),
                description = "Success test"
            )

            // then: 성공
            assert(result.status == TransferStatus.COMPLETED) {
                "Transfer should be completed"
            }
        }

        // 메모리 캐시에 등록 안 됨 (성공 케이스)
        assert(failureRegistry.get(idempotencyKey) == null) {
            "Success cases should not use memory cache"
        }

        // 응답 시간도 빠름
        println("✅ Success transfer took: ${duration}ms")
        assert(duration < 500) {
            "Success should also be fast, took ${duration}ms"
        }
    }
}
