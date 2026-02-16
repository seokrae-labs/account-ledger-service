package com.labs.ledger.application.service

import com.labs.ledger.domain.port.GetLedgerEntriesUseCase
import com.labs.ledger.domain.port.LedgerEntriesPage
import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository

class GetLedgerEntriesService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) : GetLedgerEntriesUseCase {

    override suspend fun execute(accountId: Long, page: Int, size: Int): LedgerEntriesPage {
        // Validate account exists
        accountRepository.findById(accountId)
            ?: throw AccountNotFoundException("Account not found: $accountId")

        // Calculate offset
        val offset = page.toLong() * size

        // Fetch paginated entries and total count
        val entries = ledgerEntryRepository.findByAccountId(accountId, offset, size)
        val totalElements = ledgerEntryRepository.countByAccountId(accountId)

        return LedgerEntriesPage(
            entries = entries,
            totalElements = totalElements,
            page = page,
            size = size
        )
    }
}
