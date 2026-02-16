package com.labs.ledger.infrastructure.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * Spring Security 설정
 *
 * - JWT 기반 인증
 * - Stateless (세션 사용 안 함)
 * - 엔드포인트별 접근 제어
 *
 * 테스트 환경에서는 기본적으로 비활성화되지만, "security" 프로필과 함께 사용하면 활성화됩니다.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!test | security")
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            // CSRF 비활성화 (JWT 사용 시 불필요)
            .csrf { it.disable() }

            // 접근 제어 규칙
            .authorizeExchange { exchanges ->
                exchanges
                    // Actuator health/info 엔드포인트 공개
                    .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()

                    // Swagger UI 공개
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()

                    // Dev 토큰 발급 엔드포인트 (dev 프로필에서만 빈 생성됨)
                    .pathMatchers("/api/dev/**").permitAll()

                    // API 엔드포인트는 인증 필요
                    .pathMatchers("/api/**").authenticated()

                    // 나머지는 기본적으로 인증 필요
                    .anyExchange().authenticated()
            }

            // JWT 필터 추가
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

            .build()
    }
}
