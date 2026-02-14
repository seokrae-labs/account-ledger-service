package com.labs.ledger.application.port.`in`

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
