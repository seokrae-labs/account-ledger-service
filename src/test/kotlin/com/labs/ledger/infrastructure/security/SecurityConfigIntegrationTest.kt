package com.labs.ledger.infrastructure.security

import com.labs.ledger.support.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureWebTestClient
@ActiveProfiles("test", "security")  // "test" for DB config, "security" to enable SecurityConfig
@DisplayName("SecurityConfig 통합 테스트")
class SecurityConfigIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun `Actuator health 엔드포인트는 인증 없이 접근 가능하다`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `Swagger UI는 인증 없이 접근 가능하다`() {
        webTestClient.get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus().is3xxRedirection // Redirect to swagger-ui/index.html
    }

    @Test
    fun `API 엔드포인트는 인증 없이 접근 시 401 Unauthorized를 반환한다`() {
        webTestClient.get()
            .uri("/api/accounts/1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `유효한 JWT 토큰으로 API 엔드포인트에 접근할 수 있다`() {
        // given: 유효한 JWT 토큰 생성
        val token = jwtTokenProvider.generateToken("user123", "testuser")

        // when & then
        webTestClient.get()
            .uri("/api/accounts/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isNotFound // 404 (계좌 없음) - 인증은 통과했음을 의미
    }

    @Test
    fun `유효하지 않은 JWT 토큰으로 API 접근 시 401 Unauthorized를 반환한다`() {
        // given: 유효하지 않은 토큰
        val invalidToken = "invalid.jwt.token"

        // when & then
        webTestClient.get()
            .uri("/api/accounts/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $invalidToken")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `Bearer prefix 없는 토큰으로 API 접근 시 401 Unauthorized를 반환한다`() {
        // given: Bearer prefix 없는 토큰
        val token = jwtTokenProvider.generateToken("user123")

        // when & then
        webTestClient.get()
            .uri("/api/accounts/1")
            .header(HttpHeaders.AUTHORIZATION, token) // Bearer prefix 누락
            .exchange()
            .expectStatus().isUnauthorized
    }
}
