package com.labs.ledger.adapter.out.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("transfers")
data class TransferEntity(
    @Id
    val id: Long? = null,
    val idempotencyKey: String,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: BigDecimal,
    val status: String,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
