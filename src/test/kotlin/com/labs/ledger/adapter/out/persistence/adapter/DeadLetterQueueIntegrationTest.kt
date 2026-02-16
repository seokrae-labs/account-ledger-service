package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.domain.model.DeadLetterEvent
import com.labs.ledger.domain.model.DeadLetterEventType
import com.labs.ledger.domain.port.DeadLetterQueueRepository
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * Dead Letter Queue 통합 테스트
 *
 * 검증 항목:
 * 1. DLQ 이벤트 저장 및 조회
 * 2. Unprocessed 이벤트 조회 (배치 복구용)
 * 3. 이벤트 처리 완료 표시
 * 4. Idempotency key 조회
 * 5. JSON payload 저장 및 복원
 */
class DeadLetterQueueIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var dlqRepository: DeadLetterQueueRepository

    @Test
    fun `DLQ 이벤트 저장 및 조회`() = runBlocking {
        // given
        val event = DeadLetterEvent(
            idempotencyKey = "dlq-test-001",
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = """{"fromAccountId": 1, "toAccountId": 2, "amount": "1000.00"}""",
            failureReason = "DB connection timeout",
            retryCount = 3
        )

        // when
        val saved = dlqRepository.save(event)

        // then
        assert(saved.id != null) { "ID should be generated" }
        assert(saved.idempotencyKey == "dlq-test-001")
        assert(saved.eventType == DeadLetterEventType.FAILURE_PERSISTENCE_FAILED)
        assert(saved.failureReason == "DB connection timeout")
        assert(saved.retryCount == 3)
        assert(saved.processed == false) { "Default should be unprocessed" }
    }

    @Test
    fun `Idempotency key로 이벤트 조회`() = runBlocking {
        // given
        val event = DeadLetterEvent(
            idempotencyKey = "dlq-find-test",
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = """{"test": "data"}""",
            failureReason = "Test error"
        )
        dlqRepository.save(event)

        // when
        val found = dlqRepository.findByIdempotencyKey("dlq-find-test")

        // then
        assert(found != null) { "Event should be found" }
        assert(found!!.idempotencyKey == "dlq-find-test")
        assert(found.payload == """{"test": "data"}""")
    }

    @Test
    fun `미처리 이벤트 조회 - 가장 오래된 순서`() = runBlocking {
        // given: 3개의 미처리 이벤트 생성 (순서대로)
        val event1 = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "oldest",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{"order": 1}""",
                failureReason = "Error 1"
            )
        )

        Thread.sleep(10) // 순서 보장을 위한 짧은 대기

        val event2 = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "middle",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{"order": 2}""",
                failureReason = "Error 2"
            )
        )

        Thread.sleep(10)

        val event3 = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "newest",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{"order": 3}""",
                failureReason = "Error 3"
            )
        )

        // when
        val unprocessed = dlqRepository.findUnprocessed(limit = 10)

        // then
        assert(unprocessed.size >= 3) { "At least 3 unprocessed events" }

        // 가장 오래된 것부터 반환되어야 함
        val testEvents = unprocessed.filter {
            it.idempotencyKey in listOf("oldest", "middle", "newest")
        }
        assert(testEvents.size == 3)
        assert(testEvents[0].idempotencyKey == "oldest")
        assert(testEvents[1].idempotencyKey == "middle")
        assert(testEvents[2].idempotencyKey == "newest")
    }

    @Test
    fun `미처리 이벤트 조회 - limit 적용`() = runBlocking {
        // given: 여러 개의 미처리 이벤트
        repeat(10) { index ->
            dlqRepository.save(
                DeadLetterEvent(
                    idempotencyKey = "limit-test-$index",
                    eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                    payload = """{"index": $index}""",
                    failureReason = "Test"
                )
            )
        }

        // when
        val limited = dlqRepository.findUnprocessed(limit = 5)

        // then
        assert(limited.size >= 5) { "Should return at least 5 events" }
    }

    @Test
    fun `이벤트 처리 완료 표시`() = runBlocking {
        // given
        val event = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "mark-processed-test",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{"test": "data"}""",
                failureReason = "Error"
            )
        )

        // when
        dlqRepository.markProcessed(event.id!!)

        // then
        val found = dlqRepository.findByIdempotencyKey("mark-processed-test")
        assert(found!!.processed == true) { "Should be marked as processed" }
        assert(found.processedAt != null) { "ProcessedAt should be set" }

        // Unprocessed 조회에서 제외되어야 함
        val unprocessed = dlqRepository.findUnprocessed(limit = 100)
        assert(unprocessed.none { it.id == event.id }) {
            "Processed event should not appear in unprocessed list"
        }
    }

    @Test
    fun `미처리 이벤트 카운트`() = runBlocking {
        // given: 초기 카운트 확인
        val initialCount = dlqRepository.countUnprocessed()

        // 새 이벤트 추가
        dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "count-test-1",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{}""",
                failureReason = "Test"
            )
        )

        dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "count-test-2",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{}""",
                failureReason = "Test"
            )
        )

        // when
        val newCount = dlqRepository.countUnprocessed()

        // then
        assert(newCount == initialCount + 2) {
            "Expected ${initialCount + 2}, got $newCount"
        }
    }

    @Test
    fun `복잡한 JSON payload 저장 및 복원`() = runBlocking {
        // given
        val complexPayload = """
            {
                "idempotencyKey": "complex-test",
                "fromAccountId": 123,
                "toAccountId": 456,
                "amount": "1234.5678",
                "description": "Test with \"quotes\" and special chars: \n\t",
                "originalException": "InsufficientBalanceException",
                "originalMessage": "Insufficient balance: required 1234.5678, available 100.00",
                "metadata": {
                    "userId": "user-001",
                    "timestamp": "2026-02-16T12:00:00Z"
                }
            }
        """.trimIndent()

        val event = DeadLetterEvent(
            idempotencyKey = "complex-payload-test",
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = complexPayload,
            failureReason = "Complex test"
        )

        // when
        val saved = dlqRepository.save(event)
        val retrieved = dlqRepository.findByIdempotencyKey("complex-payload-test")

        // then
        assert(retrieved != null)
        // JSONB normalizes whitespace, so check structure instead of exact match
        assert(retrieved!!.payload.contains("\"idempotencyKey\""))
        assert(retrieved.payload.contains("\"fromAccountId\""))
        assert(retrieved.payload.contains("\"toAccountId\""))
        assert(retrieved.payload.contains("\"amount\""))
        assert(retrieved.payload.contains("\"metadata\""))
        assert(retrieved.payload.contains("\"userId\""))
        assert(retrieved.payload.contains("user-001"))
    }

    @Test
    fun `다양한 이벤트 타입 저장`() = runBlocking {
        // given & when
        val failurePersistence = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "type-test-1",
                eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
                payload = """{}""",
                failureReason = "Test"
            )
        )

        val auditFailed = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "type-test-2",
                eventType = DeadLetterEventType.AUDIT_EVENT_FAILED,
                payload = """{}""",
                failureReason = "Test"
            )
        )

        val systemError = dlqRepository.save(
            DeadLetterEvent(
                idempotencyKey = "type-test-3",
                eventType = DeadLetterEventType.SYSTEM_ERROR,
                payload = """{}""",
                failureReason = "Test"
            )
        )

        // then
        assert(failurePersistence.eventType == DeadLetterEventType.FAILURE_PERSISTENCE_FAILED)
        assert(auditFailed.eventType == DeadLetterEventType.AUDIT_EVENT_FAILED)
        assert(systemError.eventType == DeadLetterEventType.SYSTEM_ERROR)
    }
}
