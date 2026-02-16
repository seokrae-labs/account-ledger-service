package com.labs.ledger.infrastructure.config

import com.labs.ledger.application.service.*
import com.labs.ledger.application.support.ExponentialBackoffRetry
import com.labs.ledger.domain.port.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {

    @Bean
    fun retryPolicy(): RetryPolicy {
        return ExponentialBackoffRetry(
            maxAttempts = 3,
            initialDelayMs = 100,
            maxDelayMs = 1000
        )
    }

    @Bean
    fun createAccountUseCase(
        accountRepository: AccountRepository
    ): CreateAccountUseCase {
        return CreateAccountService(accountRepository)
    }

    @Bean
    fun getAccountBalanceUseCase(
        accountRepository: AccountRepository
    ): GetAccountBalanceUseCase {
        return GetAccountBalanceService(accountRepository)
    }

    @Bean
    fun depositUseCase(
        accountRepository: AccountRepository,
        ledgerEntryRepository: LedgerEntryRepository,
        transactionExecutor: TransactionExecutor
    ): DepositUseCase {
        return DepositService(accountRepository, ledgerEntryRepository, transactionExecutor)
    }

    @Bean
    fun updateAccountStatusUseCase(
        accountRepository: AccountRepository,
        transactionExecutor: TransactionExecutor
    ): UpdateAccountStatusUseCase {
        return UpdateAccountStatusService(accountRepository, transactionExecutor)
    }

    @Bean
    fun transferUseCase(
        accountRepository: AccountRepository,
        ledgerEntryRepository: LedgerEntryRepository,
        transferRepository: TransferRepository,
        transactionExecutor: TransactionExecutor,
        transferAuditRepository: TransferAuditRepository,
        retryPolicy: RetryPolicy,
        deadLetterQueueRepository: DeadLetterQueueRepository
    ): TransferUseCase {
        return TransferService(
            accountRepository,
            ledgerEntryRepository,
            transferRepository,
            transactionExecutor,
            transferAuditRepository,
            retryPolicy,
            deadLetterQueueRepository
        )
    }

    @Bean
    fun getAccountsUseCase(
        accountRepository: AccountRepository
    ): GetAccountsUseCase {
        return GetAccountsService(accountRepository)
    }

    @Bean
    fun getLedgerEntriesUseCase(
        accountRepository: AccountRepository,
        ledgerEntryRepository: LedgerEntryRepository
    ): GetLedgerEntriesUseCase {
        return GetLedgerEntriesService(accountRepository, ledgerEntryRepository)
    }

    @Bean
    fun getTransfersUseCase(
        transferRepository: TransferRepository
    ): GetTransfersUseCase {
        return GetTransfersService(transferRepository)
    }
}
