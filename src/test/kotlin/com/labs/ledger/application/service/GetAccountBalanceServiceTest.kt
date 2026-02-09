package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class GetAccountBalanceServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val service = GetAccountBalanceService(accountRepository)

    @Test
    fun `계좌 조회 성공`() = runTest {
        // given
        val accountId = 1L
        val expectedAccount = Account(
            id = accountId,
            ownerName = "John Doe",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        coEvery { accountRepository.findById(accountId) } returns expectedAccount

        // when
        val result = service.execute(accountId)

        // then
        assert(result.id == accountId)
        assert(result.balance == BigDecimal("1000.00"))
        coVerify(exactly = 1) { accountRepository.findById(accountId) }
    }

    @Test
    fun `계좌 미존재시 AccountNotFoundException 발생`() = runTest {
        // given
        val accountId = 999L
        coEvery { accountRepository.findById(accountId) } returns null

        // when & then
        assertThrows<AccountNotFoundException> {
            service.execute(accountId)
        }
    }
}
