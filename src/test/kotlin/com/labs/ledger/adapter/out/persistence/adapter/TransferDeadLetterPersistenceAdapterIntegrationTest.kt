package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.domain.model.DeadLetterEntry
import com.labs.ledger.domain.model.DeadLetterEventType
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * TransferDeadLetterPersistenceAdapter 통합 테스트
 *
 * 목적: JSONB 저장/조회 검증
 */
class TransferDeadLetterPersistenceAdapterIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var adapter: TransferDeadLetterPersistenceAdapter

    @Test
    fun `DLQ 엔트리 저장 및 조회`() = runBlocking {
        // given
        val payload = """{
            "fromAccountId": 1,
            "toAccountId": 2,
            "amount": "100.00",
            "description": "Test transfer",
            "idempotencyKey": "test-key-001",
            "errorMessage": "Database connection timeout",
            "originalException": "InsufficientBalanceException"
        }""".trimIndent()

        val entry = DeadLetterEntry(
            idempotencyKey = "test-key-001",
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = payload,
            failureReason = "Database connection timeout",
            retryCount = 0
        )

        // when
        val saved = adapter.save(entry)

        // then
        assert(saved.id != null)
        assert(saved.idempotencyKey == "test-key-001")
        assert(saved.eventType == DeadLetterEventType.FAILURE_PERSISTENCE_FAILED)
        assert(saved.payload.contains("fromAccountId"))
        assert(saved.failureReason == "Database connection timeout")
        assert(saved.retryCount == 0)
        assert(saved.processed == false)
        assert(saved.processedAt == null)
    }

    @Test
    fun `idempotencyKey로 DLQ 엔트리 조회`() = runBlocking {
        // given
        val idempotencyKey = "test-key-002"
        val payload = """{"test": "data"}"""

        val entry = DeadLetterEntry(
            idempotencyKey = idempotencyKey,
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = payload,
            failureReason = "Test error"
        )
        adapter.save(entry)

        // when
        val retrieved = adapter.findByIdempotencyKey(idempotencyKey)

        // then
        assert(retrieved != null)
        assert(retrieved!!.idempotencyKey == idempotencyKey)
        assert(retrieved.payload == payload)
    }

    @Test
    fun `존재하지 않는 idempotencyKey 조회 시 null 반환`() = runBlocking {
        // when
        val retrieved = adapter.findByIdempotencyKey("non-existent-key")

        // then
        assert(retrieved == null)
    }

    @Test
    fun `JSONB payload 특수문자 처리`() = runBlocking {
        // given
        val complexPayload = """{"description":"Transfer with \"quotes\" and 'apostrophes'","metadata":{"nested":true,"array":[1,2,3]}}"""

        val entry = DeadLetterEntry(
            idempotencyKey = "test-key-003",
            eventType = DeadLetterEventType.FAILURE_PERSISTENCE_FAILED,
            payload = complexPayload
        )

        // when
        val saved = adapter.save(entry)
        val retrieved = adapter.findByIdempotencyKey("test-key-003")

        // then
        assert(retrieved != null)
        // PostgreSQL JSONB normalizes JSON (removes whitespace, reorders keys)
        // Verify by checking key content exists
        assert(retrieved!!.payload.contains("Transfer with"))
        assert(retrieved.payload.contains("quotes"))
        assert(retrieved.payload.contains("apostrophes"))
        assert(retrieved.payload.contains("nested"))
    }
}
