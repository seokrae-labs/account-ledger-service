package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.LedgerEntry

data class LedgerEntriesPage(
    val entries: List<LedgerEntry>,
    val totalElements: Long,
    val page: Int,
    val size: Int
)

interface GetLedgerEntriesUseCase {
    suspend fun execute(accountId: Long, page: Int, size: Int): LedgerEntriesPage
}
