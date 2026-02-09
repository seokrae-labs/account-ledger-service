package com.labs.ledger.adapter.out.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("accounts")
data class AccountEntity(
    @Id
    val id: Long? = null,
    val ownerName: String,
    val balance: BigDecimal,
    val status: String,
    @Version
    val version: Long = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
