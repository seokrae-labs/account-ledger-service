package com.labs.ledger.infrastructure.util

import com.labs.ledger.domain.exception.OptimisticLockException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.springframework.dao.OptimisticLockingFailureException

private val logger = KotlinLogging.logger {}

/**
 * Retry utility for handling optimistic locking conflicts in coroutine context.
 *
 * Automatically retries the given block when OptimisticLockingFailureException is thrown.
 * Uses exponential backoff strategy between retries.
 *
 * @param maxAttempts Maximum number of attempts (default: 3)
 * @param block The suspending function to retry
 * @return The result of the successful execution
 * @throws OptimisticLockingFailureException if all retry attempts are exhausted
 */
suspend fun <T> retryOnOptimisticLock(
    maxAttempts: Int = 3,
    block: suspend () -> T
): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }

    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (!isRetryableOptimisticLockException(e)) {
                throw e
            }

            val attemptNumber = attempt + 1
            logger.warn {
                "Optimistic lock conflict (${e::class.simpleName}), retrying... (attempt $attemptNumber/$maxAttempts)"
            }

            // Exponential backoff: 0ms, 100ms, 200ms
            val delayMs = when (attempt) {
                0 -> 0L      // 1st retry: immediate
                1 -> 100L    // 2nd retry: 100ms delay
                else -> 200L // 3rd+ retry: 200ms delay
            }

            if (delayMs > 0) {
                delay(delayMs)
            }
        }
    }

    // Last attempt without catching exception
    return try {
        block()
    } catch (e: Exception) {
        if (!isRetryableOptimisticLockException(e)) {
            throw e
        }

        logger.error {
            "Optimistic lock conflict (${e::class.simpleName}) exhausted all $maxAttempts attempts"
        }
        throw e
    }
}

private fun isRetryableOptimisticLockException(e: Throwable): Boolean {
    return e is OptimisticLockingFailureException || e is OptimisticLockException
}
