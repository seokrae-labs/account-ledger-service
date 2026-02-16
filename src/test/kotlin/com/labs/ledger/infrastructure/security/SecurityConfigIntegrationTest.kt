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

    @Test
    fun `api dev 엔드포인트는 인증 없이 접근 가능하다 (dev 프로필에서만 활성)`() {
        // given: DevTokenController는 test 프로필에서 비활성화되어 있음

        // when & then: 인증 없이 접근 시 404 (빈 없음)
        // 401이 아닌 404가 나오면 permitAll이 동작하는 것
        webTestClient.post()
            .uri("/api/dev/tokens")
            .bodyValue(mapOf("userId" to "test", "username" to "testuser"))
            .exchange()
            .expectStatus().isNotFound // DevTokenController 빈이 없으므로 404
    }

    @Test
    fun `api dev 경로는 SecurityConfig에서 permitAll로 설정되어 있다`() {
        // given: 인증 없이 /api/dev/** 경로 접근

        // when & then: 401 Unauthorized가 아닌 다른 상태 코드 (404 등)
        // 이는 인증 필터를 통과했음을 의미
        webTestClient.get()
            .uri("/api/dev/nonexistent")
            .exchange()
            .expectStatus().isNotFound // 404 = 인증 통과 + 경로 없음
    }
}
