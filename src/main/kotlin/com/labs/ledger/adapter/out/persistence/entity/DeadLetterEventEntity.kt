package com.labs.ledger.adapter.out.persistence.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("transfer_dead_letter_queue")
data class DeadLetterEventEntity(
    @Id
    val id: Long? = null,
    val idempotencyKey: String,
    val eventType: String,
    val payload: Json,  // JSONB type
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastRetryAt: LocalDateTime? = null,
    val processed: Boolean = false,
    val processedAt: LocalDateTime? = null
)
