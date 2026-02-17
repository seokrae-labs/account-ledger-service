package com.labs.ledger.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.support.DefaultTransactionDefinition

/**
 * R2DBC Configuration with transaction timeout settings.
 *
 * Transactions are configured with a 30-second timeout to prevent
 * long-running transactions from blocking resources.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = ["com.labs.ledger.adapter.out.persistence.repository"])
class R2dbcConfig {

    @Bean
    fun transactionalOperator(transactionManager: ReactiveTransactionManager): TransactionalOperator {
        val definition = DefaultTransactionDefinition().apply {
            timeout = 30  // 30 seconds
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }
        return TransactionalOperator.create(transactionManager, definition)
    }
}
