package com.labs.ledger.infrastructure.security

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Production 환경에서 JWT secret의 보안 강도를 검증합니다.
 *
 * - prod 프로필에서만 활성화됩니다.
 * - 애플리케이션 시작 시 placeholder 패턴을 감지하면 즉시 실패시킵니다.
 */
@Component
@Profile("prod")
class JwtSecretValidator(
    private val securityProperties: SecurityProperties
) {
    private val logger = LoggerFactory.getLogger(JwtSecretValidator::class.java)

    @PostConstruct
    fun validateJwtSecret() {
        val secret = securityProperties.jwtSecret

        logger.info("Validating JWT secret in production mode...")

        if (SecurityProperties.isPlaceholderSecret(secret)) {
            val errorMessage = """
                |=============================================================================
                |SECURITY VIOLATION: Placeholder JWT secret detected in production!
                |
                |The current JWT secret contains unsafe patterns (e.g., 'change-this',
                |'dev-only', 'default', 'example', 'placeholder').
                |
                |This is a critical security issue. The application will NOT start.
                |
                |Action Required:
                |  1. Generate a strong random secret (minimum 32 characters)
                |  2. Set it via JWT_SECRET environment variable
                |  3. Example: JWT_SECRET=$(openssl rand -base64 32)
                |
                |For more information, see: docs/SECURITY.md
                |=============================================================================
            """.trimMargin()

            logger.error(errorMessage)
            throw IllegalStateException("Production startup aborted: Unsafe JWT secret detected")
        }

        logger.info("JWT secret validation passed. Length: ${secret.length} characters")
    }
}
