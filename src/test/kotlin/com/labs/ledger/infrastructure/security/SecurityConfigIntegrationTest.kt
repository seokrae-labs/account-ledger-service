package com.labs.ledger.infrastructure.security

import com.labs.ledger.support.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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

    // ============================================================
    // POST /api/transfers 인증 강제 테스트
    // ============================================================

    @Test
    fun `POST 이체 엔드포인트는 인증 없이 접근 시 401을 반환한다`() {
        webTestClient.post()
            .uri("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "test-key-no-auth")
            .bodyValue("""{"fromAccountId":1,"toAccountId":2,"amount":"100.00"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `POST 이체 엔드포인트는 만료된 토큰으로 접근 시 401을 반환한다`() {
        // given: 음수 만료 시간으로 즉시 만료 토큰 생성
        val expiredProperties = SecurityProperties(
            jwtSecret = "test-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = -1000
        )
        val expiredToken = JwtTokenProvider(expiredProperties).generateToken("user123")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .header("Idempotency-Key", "test-key-expired")
            .bodyValue("""{"fromAccountId":1,"toAccountId":2,"amount":"100.00"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `POST 이체 엔드포인트는 변조된 서명의 토큰으로 접근 시 401을 반환한다`() {
        // given: 다른 시크릿으로 서명한 토큰
        val otherProperties = SecurityProperties(
            jwtSecret = "another-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = 3600000
        )
        val tamperedToken = JwtTokenProvider(otherProperties).generateToken("user123")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $tamperedToken")
            .header("Idempotency-Key", "test-key-tampered")
            .bodyValue("""{"fromAccountId":1,"toAccountId":2,"amount":"100.00"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `POST 이체 엔드포인트는 유효한 토큰으로 인증을 통과한다`() {
        // given: 유효한 JWT 토큰
        val token = jwtTokenProvider.generateToken("user123", "testuser")

        // when & then: 401이 아닌 응답 (404, 400, 409 등) → 인증 통과를 의미
        webTestClient.post()
            .uri("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Idempotency-Key", "test-key-valid-auth")
            .bodyValue("""{"fromAccountId":1,"toAccountId":2,"amount":"100.00"}""")
            .exchange()
            .expectStatus().value { status -> assert(status != 401) { "Expected non-401 but got $status" } }
    }
}
