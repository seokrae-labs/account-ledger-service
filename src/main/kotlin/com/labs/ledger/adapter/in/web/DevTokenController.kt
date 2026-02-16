package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.infrastructure.security.JwtTokenProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

/**
 * Dev environment JWT token issuer
 *
 * This controller is ONLY active in dev profile.
 * In production, it returns 404.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
class DevTokenController(
    private val jwtTokenProvider: JwtTokenProvider
) {

    @PostMapping("/tokens")
    suspend fun issueToken(@RequestBody request: TokenRequest): TokenResponse {
        logger.info { "Issuing dev token for userId=${request.userId}" }

        val token = jwtTokenProvider.generateToken(
            userId = request.userId,
            username = request.username
        )

        return TokenResponse(
            token = token,
            expiresIn = 86400000
        )
    }
}

data class TokenRequest(
    val userId: String,
    val username: String? = null
)

data class TokenResponse(
    val token: String,
    val expiresIn: Long
)
