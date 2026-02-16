package com.labs.ledger.infrastructure.security

/**
 * 인증된 사용자 정보
 *
 * JWT 토큰에서 추출한 사용자 식별 정보를 담습니다.
 */
data class UserPrincipal(
    /**
     * 사용자 고유 식별자
     */
    val userId: String,

    /**
     * 사용자 이름 (선택)
     */
    val username: String? = null
)
