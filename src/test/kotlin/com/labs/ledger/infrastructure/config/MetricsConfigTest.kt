package com.labs.ledger.infrastructure.config

import com.labs.ledger.application.support.InMemoryFailureRegistry
import com.labs.ledger.domain.port.FailureRecord
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MetricsConfigTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var failureRegistry: InMemoryFailureRegistry
    private lateinit var metricsConfig: MetricsConfig

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        failureRegistry = InMemoryFailureRegistry()
        metricsConfig = MetricsConfig(registry, failureRegistry)
        metricsConfig.registerMetrics()
    }

    @Test
    fun `failure_registry_size Gauge가 등록된다`() {
        val gauge = registry.find("failure_registry.size").gauge()
        assertNotNull(gauge)
    }

    @Test
    fun `초기 Gauge 값은 0이다`() {
        val gauge = registry.find("failure_registry.size").gauge()!!
        assertEquals(0.0, gauge.value())
    }

    @Test
    fun `FailureRegistry에 항목 추가 시 Gauge 값이 증가한다`() {
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = "test-key",
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            description = null
        )
        failureRegistry.register("test-key", FailureRecord(failedTransfer, "Insufficient balance"))

        val gauge = registry.find("failure_registry.size").gauge()!!
        assertEquals(1.0, gauge.value())
    }

    @Test
    fun `Caffeine 캐시 메트릭 cache_size Gauge가 등록된다`() {
        val cacheSize = registry.find("cache.size").gauge()
        assertNotNull(cacheSize)
    }
}
