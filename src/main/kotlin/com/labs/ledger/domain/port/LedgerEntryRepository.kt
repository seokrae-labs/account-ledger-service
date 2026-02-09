package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.LedgerEntry

interface LedgerEntryRepository {
    suspend fun save(entry: LedgerEntry): LedgerEntry
    suspend fun saveAll(entries: List<LedgerEntry>): List<LedgerEntry>
    suspend fun findByAccountId(accountId: Long): List<LedgerEntry>
}
