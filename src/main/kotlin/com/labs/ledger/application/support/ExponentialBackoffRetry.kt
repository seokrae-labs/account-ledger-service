package com.labs.ledger.application.support

import com.labs.ledger.domain.port.RetryPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlin.math.min

private val logger = KotlinLogging.logger {}

/**
 * Exponential backoff retry policy
 *
 * Retries failed operations with exponentially increasing delays.
 * Prevents overwhelming the system while allowing recovery from transient failures.
 *
 * Retry Schedule:
 * - Attempt 1: immediate (0ms)
 * - Attempt 2: initialDelayMs (default 100ms)
 * - Attempt 3: initialDelayMs * 2 (default 200ms)
 * - Total time: ~300ms for 3 attempts
 *
 * @property maxAttempts Maximum number of attempts (default: 3)
 * @property initialDelayMs Initial delay in milliseconds (default: 100)
 * @property maxDelayMs Maximum delay in milliseconds (default: 1000)
 */
class ExponentialBackoffRetry(
    private val maxAttempts: Int = 3,
    private val initialDelayMs: Long = 100,
    private val maxDelayMs: Long = 1000
) : RetryPolicy {

    init {
        require(maxAttempts > 0) { "Max attempts must be positive" }
        require(initialDelayMs > 0) { "Initial delay must be positive" }
        require(maxDelayMs >= initialDelayMs) { "Max delay must be >= initial delay" }
    }

    override suspend fun <T> execute(operation: suspend () -> T): T? {
        var lastException: Throwable? = null

        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Throwable) {
                lastException = e

                // Don't retry if not retriable
                if (!isRetriable(e)) {
                    logger.warn(e) { "Non-retriable exception: ${e.message}" }
                    return null
                }

                // Last attempt - don't delay
                if (attempt == maxAttempts - 1) {
                    logger.error(e) {
                        "All retry attempts exhausted (${maxAttempts} attempts): ${e.message}"
                    }
                    return null
                }

                // Calculate delay with exponential backoff
                val delayMs = calculateDelay(attempt)
                logger.warn(e) {
                    "Retry attempt ${attempt + 1}/$maxAttempts failed, retrying after ${delayMs}ms: ${e.message}"
                }
                delay(delayMs)
            }
        }

        logger.error(lastException) { "Retry logic failed unexpectedly" }
        return null
    }

    private fun calculateDelay(attempt: Int): Long {
        // Exponential: delay = initial * 2^attempt
        val exponentialDelay = initialDelayMs * (1 shl attempt)
        return min(exponentialDelay, maxDelayMs)
    }
}
