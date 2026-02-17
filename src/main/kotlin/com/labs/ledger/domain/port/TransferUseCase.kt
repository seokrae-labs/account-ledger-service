package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferCommand

interface TransferUseCase {
    suspend fun execute(command: TransferCommand): Transfer
}
