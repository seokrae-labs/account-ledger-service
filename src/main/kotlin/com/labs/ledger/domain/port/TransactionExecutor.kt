package com.labs.ledger.domain.port

interface TransactionExecutor {
    suspend fun <T> execute(block: suspend () -> T): T
}
