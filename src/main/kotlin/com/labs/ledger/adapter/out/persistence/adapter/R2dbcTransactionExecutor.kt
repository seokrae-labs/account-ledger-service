package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.domain.port.TransactionExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Component
class R2dbcTransactionExecutor(
    private val transactionalOperator: TransactionalOperator
) : TransactionExecutor {

    override suspend fun <T> execute(block: suspend () -> T): T {
        return transactionalOperator.executeAndAwait {
            block()
        } ?: throw IllegalStateException("Transaction block must return a non-null value")
    }
}
