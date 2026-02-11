package com.labs.ledger.infrastructure.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : CoWebFilter() {

    override suspend fun filter(exchange: ServerWebExchange, chain: CoWebFilterChain) {
        val traceId = exchange.request.headers.getFirst("X-Trace-Id")
            ?: UUID.randomUUID().toString().replace("-", "").take(16)

        exchange.response.headers.add("X-Trace-Id", traceId)
        MDC.put("traceId", traceId)

        val startTime = System.currentTimeMillis()

        try {
            chain.filter(exchange)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val method = exchange.request.method
            val path = exchange.request.uri.path
            val statusCode = exchange.response.statusCode?.value() ?: 0

            logger.info { "$method $path $statusCode ${duration}ms" }
            MDC.clear()
        }
    }
}
