package com.labs.ledger.infrastructure.config

import com.labs.ledger.application.service.CreateAccountService
import com.labs.ledger.application.service.DepositService
import com.labs.ledger.application.service.GetAccountBalanceService
import com.labs.ledger.application.service.TransferService
import com.labs.ledger.domain.port.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {

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
    fun transferUseCase(
        accountRepository: AccountRepository,
        ledgerEntryRepository: LedgerEntryRepository,
        transferRepository: TransferRepository,
        transactionExecutor: TransactionExecutor
    ): TransferUseCase {
        return TransferService(
            accountRepository,
            ledgerEntryRepository,
            transferRepository,
            transactionExecutor
        )
    }
}
