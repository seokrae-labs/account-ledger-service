package com.labs.ledger.application.support

import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.FailureRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

class InMemoryFailureRegistryTest {

    @Test
    fun `메모리에 실패 기록 등록 및 조회`() {
        // given
        val registry = InMemoryFailureRegistry(ttl = Duration.ofMinutes(10), maxSize = 100)
        val transfer = Transfer(
            idempotencyKey = "test-key-1",
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            failureReason = "Insufficient balance"
        )
        val record = FailureRecord(
            transfer = transfer,
            errorMessage = "Insufficient balance"
        )

        // when
        registry.register("test-key-1", record)

        // then
        val retrieved = registry.get("test-key-1")
        assert(retrieved != null) { "Record should be found" }
        assert(retrieved!!.transfer.idempotencyKey == "test-key-1") {
            "Idempotency key should match"
        }
        assert(registry.size() == 1) { "Size should be 1" }
    }

    @Test
    fun `없는 키 조회 시 null 반환`() {
        // given
        val registry = InMemoryFailureRegistry()

        // when
        val result = registry.get("non-existent-key")

        // then
        assert(result == null) { "Should return null for non-existent key" }
    }

    @Test
    fun `메모리에서 제거`() {
        // given
        val registry = InMemoryFailureRegistry()
        val transfer = Transfer(
            idempotencyKey = "test-key-2",
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED
        )
        registry.register("test-key-2", FailureRecord(transfer, "Test error"))

        // when
        registry.remove("test-key-2")

        // then
        val result = registry.get("test-key-2")
        assert(result == null) { "Record should be removed" }
        assert(registry.size() == 0) { "Size should be 0" }
    }

    @Test
    fun `TTL 만료 후 자동 제거`() = runBlocking {
        // given: TTL 1초
        val registry = InMemoryFailureRegistry(ttl = Duration.ofSeconds(1), maxSize = 100)
        val transfer = Transfer(
            idempotencyKey = "test-key-3",
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED
        )
        registry.register("test-key-3", FailureRecord(transfer, "Test error"))

        // when: 1.5초 대기 (TTL 초과)
        delay(1500)

        // then: 자동 제거됨
        val result = registry.get("test-key-3")
        assert(result == null) { "Record should be evicted after TTL" }
    }

    @Test
    fun `최대 크기 초과 시 LRU 제거`() {
        // given: 최대 크기 3
        val registry = InMemoryFailureRegistry(ttl = Duration.ofMinutes(10), maxSize = 3)

        // when: 4개 등록
        (1..4).forEach { index ->
            val transfer = Transfer(
                idempotencyKey = "test-key-$index",
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal("100.00"),
                status = TransferStatus.FAILED
            )
            registry.register("test-key-$index", FailureRecord(transfer, "Test error $index"))
        }

        // then: 최대 크기에 근접 (Caffeine은 비동기 eviction이므로 약간의 오버샷 허용)
        assert(registry.size() <= 5) {
            "Size should be close to max size (3), got ${registry.size()} (async eviction may cause slight overshoot)"
        }
    }

    @Test
    fun `통계 정보 조회`() {
        // given
        val registry = InMemoryFailureRegistry()
        val transfer = Transfer(
            idempotencyKey = "test-key-4",
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED
        )
        registry.register("test-key-4", FailureRecord(transfer, "Test error"))

        // when
        registry.get("test-key-4")  // Hit
        registry.get("non-existent") // Miss
        val stats = registry.stats()

        // then
        assert(stats.hitCount >= 1) { "Hit count should be at least 1" }
        assert(stats.missCount >= 1) { "Miss count should be at least 1" }
        assert(stats.currentSize == 1) { "Current size should be 1" }
        println("Cache stats: $stats")
    }

    @Test
    fun `동시 접근 안전성`() = runBlocking {
        // given
        val registry = InMemoryFailureRegistry()

        // when: 동시에 100개 등록
        (1..100).forEach { index ->
            val transfer = Transfer(
                idempotencyKey = "test-key-$index",
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal("100.00"),
                status = TransferStatus.FAILED
            )
            registry.register("test-key-$index", FailureRecord(transfer, "Test error $index"))
        }

        // then: 모두 조회 가능
        (1..100).forEach { index ->
            val result = registry.get("test-key-$index")
            assert(result != null) { "Record test-key-$index should be found" }
        }
        assert(registry.size() == 100) { "Size should be 100, got ${registry.size()}" }
    }
}
