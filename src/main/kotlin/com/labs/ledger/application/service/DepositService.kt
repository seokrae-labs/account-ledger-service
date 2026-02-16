package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.DepositUseCase
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.application.support.retryOnOptimisticLock
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class DepositService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val transactionExecutor: TransactionExecutor
) : DepositUseCase {

    override suspend fun execute(
        accountId: Long,
        amount: BigDecimal,
        description: String?
    ): Account {
        return retryOnOptimisticLock {
            transactionExecutor.execute {
                val account = accountRepository.findByIdForUpdate(accountId)
                    ?: throw AccountNotFoundException("Account not found: $accountId")

                val updatedAccount = account.deposit(amount)
                val savedAccount = accountRepository.save(updatedAccount)

                ledgerEntryRepository.save(
                    LedgerEntry(
                        accountId = savedAccount.id!!,
                        type = LedgerEntryType.CREDIT,
                        amount = amount,
                        description = description
                    )
                )

                logger.info { "Deposit completed: accountId=$accountId, amount=$amount" }
                savedAccount
            }
        }
    }
}
