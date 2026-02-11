package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.GetAccountBalanceUseCase
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class GetAccountBalanceService(
    private val accountRepository: AccountRepository
) : GetAccountBalanceUseCase {

    override suspend fun execute(accountId: Long): Account {
        logger.debug { "Fetching account: id=$accountId" }
        return accountRepository.findById(accountId)
            ?: throw AccountNotFoundException("Account not found: $accountId")
    }
}
