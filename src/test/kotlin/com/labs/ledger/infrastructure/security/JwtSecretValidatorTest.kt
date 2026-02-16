package com.labs.ledger.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/**
 * JwtSecretValidator의 보안 검증 로직을 테스트합니다.
 */
class JwtSecretValidatorTest {

    @Test
    fun `placeholder 패턴 감지 - change-this 포함`() {
        val secret = "change-this-secret-key-in-production"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `placeholder 패턴 감지 - dev-only 포함`() {
        val secret = "dev-only-secret-key-for-testing"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `placeholder 패턴 감지 - default 포함`() {
        val secret = "default-jwt-secret-key"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `placeholder 패턴 감지 - example 포함`() {
        val secret = "example-secret-key-12345"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `placeholder 패턴 감지 - placeholder 포함`() {
        val secret = "placeholder-jwt-secret"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `placeholder 패턴 감지 - 대소문자 무관`() {
        val secret = "CHANGE-THIS-SECRET-KEY"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isTrue()
    }

    @Test
    fun `안전한 secret은 placeholder로 감지되지 않음`() {
        val secret = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isFalse()
    }

    @Test
    fun `무작위 Base64 secret은 안전함`() {
        val secret = "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="
        assertThat(SecurityProperties.isPlaceholderSecret(secret)).isFalse()
    }

    @Test
    fun `prod 환경에서 placeholder secret으로 시작 시 실패`() {
        val properties = SecurityProperties(
            jwtSecret = "change-this-secret-key-minimum-256-bits-required-for-hs256-algorithm",
            jwtExpirationMs = 86400000
        )

        val validator = JwtSecretValidator(properties)

        val exception = assertThrows<IllegalStateException> {
            validator.validateJwtSecret()
        }

        assertThat(exception.message).contains("Unsafe JWT secret detected")
    }

    @Test
    fun `prod 환경에서 안전한 secret으로 시작 시 성공`() {
        val properties = SecurityProperties(
            jwtSecret = "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=",
            jwtExpirationMs = 86400000
        )

        val validator = JwtSecretValidator(properties)

        assertDoesNotThrow {
            validator.validateJwtSecret()
        }
    }

    @Test
    fun `SecurityProperties init에서 최소 길이 미달 시 실패`() {
        val exception = assertThrows<IllegalArgumentException> {
            SecurityProperties(
                jwtSecret = "too-short",  // 9 characters < 32
                jwtExpirationMs = 86400000
            )
        }

        assertThat(exception.message).contains("must be at least 32 characters")
    }

    @Test
    fun `SecurityProperties init에서 32자 이상 secret 허용`() {
        assertDoesNotThrow {
            SecurityProperties(
                jwtSecret = "a".repeat(32),  // Exactly 32 characters
                jwtExpirationMs = 86400000
            )
        }
    }
}
