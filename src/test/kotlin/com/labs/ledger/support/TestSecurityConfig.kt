package com.labs.ledger.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

/**
 * 테스트용 보안 설정
 *
 * 모든 엔드포인트를 permitAll()로 설정하여 테스트 시 인증 불필요
 *
 * @Profile("!security"): security 프로필이 활성화되지 않은 경우에만 로드
 * - 일반 테스트: TestSecurityConfig 사용 (Security 무시)
 * - SecurityConfigIntegrationTest: 실제 SecurityConfig 사용
 */
@TestConfiguration
@Profile("!security")
class TestSecurityConfig {

    @Bean
    fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges.anyExchange().permitAll()
            }
            .build()
    }
}
