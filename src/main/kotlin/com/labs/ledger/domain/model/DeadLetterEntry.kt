package com.labs.ledger.domain.model

import java.time.LocalDateTime

/**
 * Immutable entry in the Dead Letter Queue
 *
 * Design:
 * - Fallback for events that failed all retry attempts
 * - JSONB payload contains full context for manual recovery
 * - Independent persistence (no FK constraints)
 * - Processed flag enables batch recovery workflows
 *
 * @property id Auto-generated entry ID
 * @property idempotencyKey Correlation key for deduplication
 * @property eventType Categorization of failure type
 * @property payload JSONB string with full event context
 * @property failureReason Last error message before DLQ
 * @property retryCount Number of retry attempts before DLQ
 * @property createdAt When entry was created
 * @property lastRetryAt Timestamp of last retry attempt
 * @property processed Whether this entry has been manually recovered
 * @property processedAt When manual recovery completed
 */
data class DeadLetterEntry(
    val id: Long? = null,
    val idempotencyKey: String,
    val eventType: DeadLetterEventType,
    val payload: String,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastRetryAt: LocalDateTime? = null,
    val processed: Boolean = false,
    val processedAt: LocalDateTime? = null
)
