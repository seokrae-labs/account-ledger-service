package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.TransactionExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class UpdateAccountStatusServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val transactionExecutor: TransactionExecutor = mockk()
    private val service = UpdateAccountStatusService(accountRepository, transactionExecutor)

    @Test
    fun `ACTIVE 계좌를 SUSPENDED로 변경 성공`() = runTest {
        val accountId = 1L
        val activeAccount = Account(
            id = accountId,
            ownerName = "John",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val suspendedAccount = activeAccount.copy(status = AccountStatus.SUSPENDED, version = 1L)

        coEvery { transactionExecutor.execute<Account>(any()) } coAnswers {
            firstArg<suspend () -> Account>().invoke()
        }
        coEvery { accountRepository.findByIdForUpdate(accountId) } returns activeAccount
        coEvery { accountRepository.save(any()) } returns suspendedAccount

        val result = service.execute(accountId, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
        coVerify(exactly = 1) { accountRepository.findByIdForUpdate(accountId) }
        coVerify(exactly = 1) { accountRepository.save(any()) }
    }

    @Test
    fun `계좌 미존재 시 AccountNotFoundException 발생`() = runTest {
        val accountId = 999L

        coEvery { transactionExecutor.execute<Account>(any()) } coAnswers {
            firstArg<suspend () -> Account>().invoke()
        }
        coEvery { accountRepository.findByIdForUpdate(accountId) } returns null

        assertThrows<AccountNotFoundException> {
            service.execute(accountId, AccountStatus.SUSPENDED)
        }

        coVerify(exactly = 1) { accountRepository.findByIdForUpdate(accountId) }
        coVerify(exactly = 0) { accountRepository.save(any()) }
    }
}
