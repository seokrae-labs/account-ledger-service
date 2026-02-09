package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account
import java.math.BigDecimal

interface DepositUseCase {
    suspend fun execute(accountId: Long, amount: BigDecimal, description: String?): Account
}
