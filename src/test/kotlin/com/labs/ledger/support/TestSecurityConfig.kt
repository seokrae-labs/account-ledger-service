package com.labs.ledger.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 테스트용 보안 설정
 *
 * 모든 엔드포인트를 permitAll()로 설정하여 테스트 시 인증 불필요
 *
 * @Profile("!security"): security 프로필이 활성화되지 않은 경우에만 로드
 * - 일반 테스트: TestSecurityConfig 사용 (Security 무시)
 * - SecurityConfigIntegrationTest: 실제 SecurityConfig 사용
 *
 * ServerHttpSecurity 의존성 없이 Mock SecurityWebFilterChain 직접 생성
 */
@TestConfiguration
@Profile("!security")
class TestSecurityConfig {

    @Bean
    fun testSecurityWebFilterChain(): SecurityWebFilterChain {
        return object : SecurityWebFilterChain {
            override fun matches(exchange: ServerWebExchange): Mono<Boolean> = Mono.just(true)
            override fun getWebFilters(): Flux<WebFilter> = Flux.empty()
        }
    }

    @Bean
    fun springSecurityFilterChain(): WebFilterChainProxy {
        return WebFilterChainProxy(listOf(testSecurityWebFilterChain()))
    }
}
