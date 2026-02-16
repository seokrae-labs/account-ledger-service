package com.labs.ledger.infrastructure.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

/**
 * JWT 인증 필터
 *
 * HTTP 요청의 Authorization 헤더에서 JWT 토큰을 추출하고 검증합니다.
 * 유효한 토큰이면 SecurityContext에 인증 정보를 저장합니다.
 *
 * 테스트 환경에서는 기본적으로 비활성화되지만, "security" 프로필과 함께 사용하면 활성화됩니다.
 */
@Component
@Profile("!test | security")
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : WebFilter {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val token = extractToken(exchange)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            val userPrincipal = jwtTokenProvider.getUserPrincipal(token)
            val authentication = UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                emptyList() // authorities (역할/권한은 향후 추가 가능)
            )

            logger.debug { "Authenticated user: ${userPrincipal.userId}" }

            return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
        }

        // 토큰이 없거나 유효하지 않으면 그냥 다음 필터로 진행
        // SecurityConfig에서 authenticated() 규칙으로 차단됨
        return chain.filter(exchange)
    }

    /**
     * Authorization 헤더에서 JWT 토큰 추출
     *
     * @param exchange ServerWebExchange
     * @return JWT 토큰 또는 null
     */
    private fun extractToken(exchange: ServerWebExchange): String? {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length)
        }

        return null
    }
}
