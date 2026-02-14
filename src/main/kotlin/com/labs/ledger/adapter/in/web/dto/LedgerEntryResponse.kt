package com.labs.ledger.adapter.`in`.web.dto

import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import java.math.BigDecimal
import java.time.LocalDateTime

data class LedgerEntryResponse(
    val id: Long?,
    val accountId: Long,
    val type: LedgerEntryType,
    val amount: BigDecimal,
    val referenceId: String?,
    val description: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(ledgerEntry: LedgerEntry): LedgerEntryResponse {
            return LedgerEntryResponse(
                id = ledgerEntry.id,
                accountId = ledgerEntry.accountId,
                type = ledgerEntry.type,
                amount = ledgerEntry.amount,
                referenceId = ledgerEntry.referenceId,
                description = ledgerEntry.description,
                createdAt = ledgerEntry.createdAt
            )
        }
    }
}
