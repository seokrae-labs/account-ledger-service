package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account

interface GetAccountBalanceUseCase {
    suspend fun execute(accountId: Long): Account
}
