package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.LedgerEntry

interface LedgerEntryRepository {
    suspend fun save(entry: LedgerEntry): LedgerEntry
    suspend fun saveAll(entries: List<LedgerEntry>): List<LedgerEntry>
    suspend fun findByAccountId(accountId: Long): List<LedgerEntry>

    // Pagination support
    suspend fun findByAccountId(accountId: Long, offset: Long, limit: Int): List<LedgerEntry>
    suspend fun countByAccountId(accountId: Long): Long
}
