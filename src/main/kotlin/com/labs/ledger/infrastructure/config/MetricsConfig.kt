package com.labs.ledger.infrastructure.config

import com.labs.ledger.application.support.InMemoryFailureRegistry
import com.labs.ledger.domain.port.FailureRegistry
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig(
    private val registry: MeterRegistry,
    private val failureRegistry: FailureRegistry
) {

    @PostConstruct
    fun registerMetrics() {
        Gauge.builder("failure_registry.size", failureRegistry) { it.size().toDouble() }
            .description("Current size of in-memory failure registry")
            .register(registry)

        if (failureRegistry is InMemoryFailureRegistry) {
            CaffeineCacheMetrics.monitor(registry, failureRegistry.getCache(), "failureRegistry")
        }
    }
}
