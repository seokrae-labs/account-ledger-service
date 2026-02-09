package com.labs.ledger.adapter.out.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("ledger_entries")
data class LedgerEntryEntity(
    @Id
    val id: Long? = null,
    val accountId: Long,
    val type: String,
    val amount: BigDecimal,
    val referenceId: String? = null,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
