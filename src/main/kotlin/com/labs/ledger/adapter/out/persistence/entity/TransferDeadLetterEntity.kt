package com.labs.ledger.adapter.out.persistence.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * R2DBC Entity for transfer_dead_letter_queue table
 *
 * Design:
 * - Maps to transfer_dead_letter_queue schema (V4 migration)
 * - payload uses R2DBC PostgreSQL Json type for JSONB column
 */
@Table("transfer_dead_letter_queue")
data class TransferDeadLetterEntity(
    @Id
    val id: Long? = null,
    val idempotencyKey: String,
    val eventType: String,
    val payload: Json,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastRetryAt: LocalDateTime? = null,
    val processed: Boolean = false,
    val processedAt: LocalDateTime? = null
)
