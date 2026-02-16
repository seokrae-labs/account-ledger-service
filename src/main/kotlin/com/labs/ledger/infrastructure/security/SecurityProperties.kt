package com.labs.ledger.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 보안 설정 프로퍼티
 *
 * application.yml의 app.security 섹션에서 값을 주입받습니다.
 */
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    /**
     * JWT 서명에 사용할 비밀키
     * 최소 256비트 (32자) 이상 권장
     */
    val jwtSecret: String,

    /**
     * JWT 토큰 만료 시간 (밀리초)
     * 기본값: 86400000 (24시간)
     */
    val jwtExpirationMs: Long = 86400000
) {
    init {
        require(jwtSecret.length >= 32) {
            "JWT secret must be at least 32 characters (256 bits) for HS256 algorithm. Current length: ${jwtSecret.length}"
        }
    }

    companion object {
        /**
         * Placeholder 패턴 감지: 운영 환경에서 안전하지 않은 기본값 차단
         */
        fun isPlaceholderSecret(secret: String): Boolean {
            val lowercaseSecret = secret.lowercase()
            val dangerousPatterns = listOf(
                "change-this",
                "dev-only",
                "default",
                "example",
                "placeholder",
                "test-secret",
                "sample"
            )
            return dangerousPatterns.any { pattern -> lowercaseSecret.contains(pattern) }
        }
    }
}
