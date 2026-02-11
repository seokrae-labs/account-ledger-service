package com.labs.ledger.application.service

import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.CreateAccountUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class CreateAccountService(
    private val accountRepository: AccountRepository
) : CreateAccountUseCase {

    override suspend fun execute(ownerName: String): Account {
        val account = Account(
            ownerName = ownerName,
            balance = BigDecimal.ZERO,
            status = AccountStatus.ACTIVE
        )
        val saved = accountRepository.save(account)
        logger.info { "Account created: id=${saved.id}, owner=$ownerName" }
        return saved
    }
}
