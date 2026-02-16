package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account

data class AccountsPage(
    val accounts: List<Account>,
    val totalElements: Long,
    val page: Int,
    val size: Int
)

interface GetAccountsUseCase {
    suspend fun execute(page: Int, size: Int): AccountsPage
}
