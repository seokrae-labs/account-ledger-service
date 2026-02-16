package com.labs.ledger.application.service

import com.labs.ledger.domain.port.AccountsPage
import com.labs.ledger.domain.port.GetAccountsUseCase
import com.labs.ledger.domain.port.AccountRepository

class GetAccountsService(
    private val accountRepository: AccountRepository
) : GetAccountsUseCase {

    override suspend fun execute(page: Int, size: Int): AccountsPage {
        val offset = page.toLong() * size
        val accounts = accountRepository.findAll(offset, size)
        val totalElements = accountRepository.count()

        return AccountsPage(
            accounts = accounts,
            totalElements = totalElements,
            page = page,
            size = size
        )
    }
}
