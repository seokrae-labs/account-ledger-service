package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.DeadLetterEvent

/**
 * Repository for dead letter queue events
 *
 * Stores events that failed after all retry attempts.
 * Enables manual recovery or batch processing.
 */
interface DeadLetterQueueRepository {
    /**
     * Save a dead letter event
     *
     * @param event The event to save
     * @return The saved event with generated ID
     */
    suspend fun save(event: DeadLetterEvent): DeadLetterEvent

    /**
     * Find unprocessed events for batch recovery
     *
     * @param limit Maximum number of events to return
     * @return List of unprocessed events, ordered by creation time (oldest first)
     */
    suspend fun findUnprocessed(limit: Int = 100): List<DeadLetterEvent>

    /**
     * Mark an event as processed
     *
     * @param id Event ID
     */
    suspend fun markProcessed(id: Long)

    /**
     * Count unprocessed events (for monitoring)
     *
     * @return Number of unprocessed events
     */
    suspend fun countUnprocessed(): Long

    /**
     * Find event by idempotency key
     *
     * @param idempotencyKey The idempotency key
     * @return The event if found, null otherwise
     */
    suspend fun findByIdempotencyKey(idempotencyKey: String): DeadLetterEvent?
}
