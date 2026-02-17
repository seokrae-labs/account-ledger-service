package com.labs.ledger.infrastructure.web

import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

/**
 * WebFilter that applies a timeout to HTTP requests.
 *
 * Prevents requests from running indefinitely by enforcing a 60-second timeout.
 * If a request exceeds the timeout, it returns 504 Gateway Timeout.
 */
@Component
@Order(1)  // Execute early in the filter chain
class TimeoutFilter : WebFilter {

    companion object {
        private val REQUEST_TIMEOUT = Duration.ofSeconds(60)
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return chain.filter(exchange)
            .timeout(REQUEST_TIMEOUT)
            .onErrorResume(TimeoutException::class.java) {
                val response = exchange.response
                response.statusCode = HttpStatus.GATEWAY_TIMEOUT
                response.headers.contentType = MediaType.APPLICATION_JSON
                val body = """{"error":"GATEWAY_TIMEOUT","message":"Request timed out","timestamp":"${Instant.now()}"}"""
                val buffer = DefaultDataBufferFactory().wrap(body.toByteArray())
                response.writeWith(Mono.just(buffer))
            }
    }
}
