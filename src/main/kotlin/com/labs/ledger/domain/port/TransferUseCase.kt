package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Transfer
import java.math.BigDecimal

interface TransferUseCase {
    suspend fun execute(
        idempotencyKey: String,
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        description: String?
    ): Transfer
}
