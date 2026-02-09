package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account

interface CreateAccountUseCase {
    suspend fun execute(ownerName: String): Account
}
