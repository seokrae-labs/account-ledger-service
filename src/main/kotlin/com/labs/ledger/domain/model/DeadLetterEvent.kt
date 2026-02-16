package com.labs.ledger.domain.model

import java.time.LocalDateTime

/**
 * Dead letter event for failed operations
 *
 * Represents an event that failed after all retry attempts.
 * Stored in DLQ for manual recovery or batch processing.
 *
 * @property id Auto-generated event ID
 * @property idempotencyKey Original transfer idempotency key
 * @property eventType Type of failed event
 * @property payload Full context as JSON string for recovery
 * @property failureReason Last error message
 * @property retryCount Number of retry attempts before DLQ
 * @property createdAt When the event was added to DLQ
 * @property lastRetryAt Last retry attempt timestamp
 * @property processed Whether this event has been manually resolved
 * @property processedAt When the event was resolved
 */
data class DeadLetterEvent(
    val id: Long? = null,
    val idempotencyKey: String,
    val eventType: DeadLetterEventType,
    val payload: String,  // JSON
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastRetryAt: LocalDateTime? = null,
    val processed: Boolean = false,
    val processedAt: LocalDateTime? = null
)

/**
 * Types of dead letter events
 */
enum class DeadLetterEventType {
    /**
     * Failed to persist FAILED transfer state after retries
     */
    FAILURE_PERSISTENCE_FAILED,

    /**
     * Failed to record audit event after retries
     */
    AUDIT_EVENT_FAILED,

    /**
     * System error during transfer execution
     */
    SYSTEM_ERROR
}
