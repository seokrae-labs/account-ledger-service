package com.labs.ledger.domain.model

/**
 * Event types for Dead Letter Queue
 *
 * Categorizes the reason why an event was sent to DLQ
 */
enum class DeadLetterEventType {
    /**
     * Failed to persist transfer failure to database
     * (fallback from persistFailureAsync)
     */
    FAILURE_PERSISTENCE_FAILED
}
