package com.labs.ledger.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider

    @BeforeEach
    fun setup() {
        val properties = SecurityProperties(
            jwtSecret = "test-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = 3600000 // 1 hour
        )
        jwtTokenProvider = JwtTokenProvider(properties)
    }

    @Test
    fun `JWT 토큰을 생성할 수 있다`() {
        // given
        val userId = "user123"
        val username = "testuser"

        // when
        val token = jwtTokenProvider.generateToken(userId, username)

        // then
        assertThat(token).isNotBlank()
        assertThat(token.split(".")).hasSize(3) // JWT: header.payload.signature
    }

    @Test
    fun `생성한 JWT 토큰을 검증할 수 있다`() {
        // given
        val userId = "user123"
        val token = jwtTokenProvider.generateToken(userId)

        // when
        val isValid = jwtTokenProvider.validateToken(token)

        // then
        assertThat(isValid).isTrue()
    }

    @Test
    fun `유효하지 않은 토큰은 검증에 실패한다`() {
        // given
        val invalidToken = "invalid.token.here"

        // when
        val isValid = jwtTokenProvider.validateToken(invalidToken)

        // then
        assertThat(isValid).isFalse()
    }

    @Test
    fun `빈 토큰은 검증에 실패한다`() {
        // given
        val emptyToken = ""

        // when
        val isValid = jwtTokenProvider.validateToken(emptyToken)

        // then
        assertThat(isValid).isFalse()
    }

    @Test
    fun `JWT 토큰에서 UserPrincipal을 추출할 수 있다`() {
        // given
        val userId = "user123"
        val username = "testuser"
        val token = jwtTokenProvider.generateToken(userId, username)

        // when
        val userPrincipal = jwtTokenProvider.getUserPrincipal(token)

        // then
        assertThat(userPrincipal.userId).isEqualTo(userId)
        assertThat(userPrincipal.username).isEqualTo(username)
    }

    @Test
    fun `username 없이 생성한 토큰에서도 UserPrincipal을 추출할 수 있다`() {
        // given
        val userId = "user123"
        val token = jwtTokenProvider.generateToken(userId)

        // when
        val userPrincipal = jwtTokenProvider.getUserPrincipal(token)

        // then
        assertThat(userPrincipal.userId).isEqualTo(userId)
        assertThat(userPrincipal.username).isNull()
    }

    @Test
    fun `만료된 토큰은 검증에 실패한다`() {
        // given: 만료 시간을 음수로 설정 (즉시 만료)
        val expiredProperties = SecurityProperties(
            jwtSecret = "test-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = -1000
        )
        val expiredTokenProvider = JwtTokenProvider(expiredProperties)
        val token = expiredTokenProvider.generateToken("user123")

        // when
        val isValid = jwtTokenProvider.validateToken(token)

        // then
        assertThat(isValid).isFalse()
    }

    @Test
    fun `다른 비밀키로 생성한 토큰은 검증에 실패한다`() {
        // given: 다른 비밀키로 TokenProvider 생성
        val otherProperties = SecurityProperties(
            jwtSecret = "another-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = 3600000
        )
        val otherTokenProvider = JwtTokenProvider(otherProperties)
        val token = otherTokenProvider.generateToken("user123")

        // when: 현재 TokenProvider로 검증
        val isValid = jwtTokenProvider.validateToken(token)

        // then
        assertThat(isValid).isFalse()
    }
}
