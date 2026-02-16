package com.labs.ledger.infrastructure.security

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SecurityException
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

private val logger = KotlinLogging.logger {}

/**
 * JWT 토큰 생성 및 검증
 *
 * - 토큰 생성: userId를 subject로 저장
 * - 토큰 검증: 서명 및 만료 시간 확인
 * - 클레임 추출: userId, username 등
 *
 * 테스트 환경에서는 기본적으로 비활성화되지만, "security" 프로필과 함께 사용하면 활성화됩니다.
 */
@Component
@EnableConfigurationProperties(SecurityProperties::class)
@Profile("!test | security")
class JwtTokenProvider(
    private val properties: SecurityProperties
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray())

    /**
     * JWT 토큰 생성
     *
     * @param userId 사용자 ID (subject)
     * @param username 사용자 이름 (선택, claim)
     * @return JWT 토큰 문자열
     */
    fun generateToken(userId: String, username: String? = null): String {
        val now = Date()
        val expiryDate = Date(now.time + properties.jwtExpirationMs)

        val claims = mutableMapOf<String, Any>()
        username?.let { claims["username"] = it }

        return Jwts.builder()
            .subject(userId)
            .claims(claims)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    /**
     * JWT 토큰 검증
     *
     * @param token JWT 토큰
     * @return 유효하면 true, 그렇지 않으면 false
     */
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
            true
        } catch (ex: SecurityException) {
            logger.warn { "Invalid JWT signature: ${ex.message}" }
            false
        } catch (ex: MalformedJwtException) {
            logger.warn { "Invalid JWT token: ${ex.message}" }
            false
        } catch (ex: ExpiredJwtException) {
            logger.warn { "Expired JWT token: ${ex.message}" }
            false
        } catch (ex: UnsupportedJwtException) {
            logger.warn { "Unsupported JWT token: ${ex.message}" }
            false
        } catch (ex: IllegalArgumentException) {
            logger.warn { "JWT claims string is empty: ${ex.message}" }
            false
        }
    }

    /**
     * JWT 토큰에서 UserPrincipal 추출
     *
     * @param token JWT 토큰
     * @return UserPrincipal (userId, username)
     */
    fun getUserPrincipal(token: String): UserPrincipal {
        val claims = getClaims(token)
        val userId = claims.subject
        val username = claims["username"] as? String

        return UserPrincipal(userId = userId, username = username)
    }

    /**
     * JWT 토큰에서 Claims 추출
     *
     * @param token JWT 토큰
     * @return Claims 객체
     */
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
