package com.labs.ledger.adapter.out.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("transfer_audit_events")
data class TransferAuditEventEntity(
    @Id
    val id: Long? = null,
    val transferId: Long? = null,
    val idempotencyKey: String,
    val eventType: String,
    val transferStatus: String? = null,
    val reasonCode: String? = null,
    val reasonMessage: String? = null,
    val metadata: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
