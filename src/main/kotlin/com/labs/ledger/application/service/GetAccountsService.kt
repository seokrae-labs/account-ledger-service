package com.labs.ledger.application.service

import com.labs.ledger.application.port.`in`.AccountsPage
import com.labs.ledger.application.port.`in`.GetAccountsUseCase
import com.labs.ledger.domain.port.AccountRepository
import org.springframework.stereotype.Service

@Service
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
