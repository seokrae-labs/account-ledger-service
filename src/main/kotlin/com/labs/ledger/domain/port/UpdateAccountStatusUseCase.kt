package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus

interface UpdateAccountStatusUseCase {
    suspend fun execute(accountId: Long, targetStatus: AccountStatus): Account
}
