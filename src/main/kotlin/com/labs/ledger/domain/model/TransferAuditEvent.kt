package com.labs.ledger.domain.model

import java.time.LocalDateTime

/**
 * Immutable audit event for transfer lifecycle tracking
 *
 * Design:
 * - Append-only: events are never updated or deleted
 * - Independent persistence: survives main transaction rollback
 * - Application-level reference: no FK to transfers table
 *
 * @property id Auto-generated event ID
 * @property transferId Reference to transfer (nullable: may not exist after rollback)
 * @property idempotencyKey Always present for correlation
 * @property eventType Lifecycle event type
 * @property transferStatus Transfer status at event time
 * @property reasonCode Exception class name or error code
 * @property reasonMessage Human-readable error message
 * @property metadata Additional context as JSON string
 * @property createdAt Event timestamp
 */
data class TransferAuditEvent(
    val id: Long? = null,
    val transferId: Long? = null,
    val idempotencyKey: String,
    val eventType: TransferAuditEventType,
    val transferStatus: TransferStatus? = null,
    val reasonCode: String? = null,
    val reasonMessage: String? = null,
    val metadata: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
