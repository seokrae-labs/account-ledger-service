package com.labs.ledger.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.labs.ledger.application.service.*
import com.labs.ledger.application.support.InMemoryFailureRegistry
import com.labs.ledger.domain.port.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class UseCaseConfig {

    @Bean
    fun failureRegistry(): FailureRegistry {
        return InMemoryFailureRegistry(
            ttl = Duration.ofHours(1),
            maxSize = 10_000
        )
    }

    @Bean
    fun asyncCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
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
        failureRegistry: FailureRegistry,
        deadLetterRepository: DeadLetterRepository,
        objectMapper: ObjectMapper,
        asyncCoroutineScope: CoroutineScope
    ): TransferUseCase {
        return TransferService(
            accountRepository,
            ledgerEntryRepository,
            transferRepository,
            transactionExecutor,
            transferAuditRepository,
            failureRegistry,
            deadLetterRepository,
            objectMapper,
            asyncCoroutineScope
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
