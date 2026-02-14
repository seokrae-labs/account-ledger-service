package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.UpdateAccountStatusUseCase
import com.labs.ledger.infrastructure.util.retryOnOptimisticLock
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class UpdateAccountStatusService(
    private val accountRepository: AccountRepository,
    private val transactionExecutor: TransactionExecutor
) : UpdateAccountStatusUseCase {

    override suspend fun execute(accountId: Long, targetStatus: AccountStatus): Account {
        return retryOnOptimisticLock {
            transactionExecutor.execute {
                val account = accountRepository.findByIdForUpdate(accountId)
                    ?: throw AccountNotFoundException("Account not found: $accountId")

                val updatedAccount = when (targetStatus) {
                    AccountStatus.SUSPENDED -> account.suspend()
                    AccountStatus.ACTIVE -> account.activate()
                    AccountStatus.CLOSED -> account.close()
                }

                val savedAccount = accountRepository.save(updatedAccount)

                logger.info { "Account status updated: accountId=$accountId, status=${targetStatus.name}" }
                savedAccount
            }
        }
    }
}
