package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Transfer
import java.time.Instant

/**
 * In-memory registry for tracking failed transfers before DB persistence
 *
 * Purpose:
 * - Provides immediate idempotency check without DB query
 * - Enables async failure persistence without blocking client response
 * - Acts as temporary cache until DB write completes
 *
 * Design:
 * - Thread-safe operations for concurrent access
 * - TTL-based automatic cleanup to prevent memory leaks
 * - Fallback to DB on cache miss (server restart scenario)
 */
interface FailureRegistry {
    /**
     * Register a failed transfer immediately (synchronous, in-memory)
     *
     * @param idempotencyKey Unique transfer identifier
     * @param record Failure details
     */
    fun register(idempotencyKey: String, record: FailureRecord)

    /**
     * Check if a transfer has been marked as failed
     *
     * @param idempotencyKey Unique transfer identifier
     * @return Failure record if found, null otherwise
     */
    fun get(idempotencyKey: String): FailureRecord?

    /**
     * Remove a failure record (called after successful DB persistence)
     *
     * @param idempotencyKey Unique transfer identifier
     */
    fun remove(idempotencyKey: String)

    /**
     * Get current registry size (for monitoring)
     */
    fun size(): Int
}

/**
 * Immutable failure record stored in memory
 *
 * @property transfer The failed transfer details
 * @property errorMessage Human-readable error message
 * @property timestamp When the failure was registered
 */
data class FailureRecord(
    val transfer: Transfer,
    val errorMessage: String,
    val timestamp: Instant = Instant.now()
)
