package com.labs.ledger.application.service

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

class CreateAccountServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val service = CreateAccountService(accountRepository)

    @Test
    fun `계좌 생성 성공`() = runTest {
        // given
        val ownerName = "John Doe"
        val expectedAccount = Account(
            id = 1L,
            ownerName = ownerName,
            balance = BigDecimal.ZERO,
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        coEvery { accountRepository.save(any()) } returns expectedAccount

        // when
        val result = service.execute(ownerName)

        // then
        assert(result.id == 1L)
        assert(result.ownerName == ownerName)
        assert(result.balance == BigDecimal.ZERO)
        coVerify(exactly = 1) { accountRepository.save(any()) }
    }

    @Test
    fun `저장소 예외 전파`() = runTest {
        // given
        val ownerName = "Jane Doe"
        coEvery { accountRepository.save(any()) } throws RuntimeException("Database error")

        // when & then
        assertThrows<RuntimeException> {
            service.execute(ownerName)
        }
    }
}
