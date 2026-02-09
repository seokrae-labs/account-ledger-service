package com.labs.ledger.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class LedgerEntry(
    val id: Long? = null,
    val accountId: Long,
    val type: LedgerEntryType,
    val amount: BigDecimal,
    val referenceId: String? = null,
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
    }
}
