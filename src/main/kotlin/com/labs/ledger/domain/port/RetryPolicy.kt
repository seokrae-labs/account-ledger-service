package com.labs.ledger.domain.port

/**
 * Retry policy for transient failures
 *
 * Provides automatic retry mechanism for operations that may fail temporarily.
 * Implements exponential backoff to avoid overwhelming the system.
 *
 * Usage:
 * ```kotlin
 * val result = retryPolicy.execute {
 *     // Potentially failing operation
 *     repository.save(entity)
 * }
 * ```
 */
interface RetryPolicy {
    /**
     * Execute an operation with retry logic
     *
     * @param operation The suspend function to execute
     * @return Result of the operation, or null if all retries failed
     */
    suspend fun <T> execute(operation: suspend () -> T): T?

    /**
     * Check if an exception should trigger a retry
     *
     * @param exception The exception that occurred
     * @return true if the operation should be retried, false otherwise
     */
    fun isRetriable(exception: Throwable): Boolean {
        // Default: retry for most exceptions except domain exceptions
        return exception !is com.labs.ledger.domain.exception.DomainException
    }
}
