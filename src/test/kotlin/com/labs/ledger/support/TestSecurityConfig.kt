package com.labs.ledger.support

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * 테스트용 보안 설정
 *
 * 모든 엔드포인트를 permitAll()로 설정하여 테스트 시 인증 불필요
 *
 * @WebFluxTest에서 사용 시:
 * - Spring Security Auto Configuration 제외 필요
 * - 이 TestConfig가 간단한 SecurityWebFilterChain 제공
 */
@TestConfiguration
class TestSecurityConfig {

    @Bean
    fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges.anyExchange().permitAll()
            }
            .build()
    }
}
