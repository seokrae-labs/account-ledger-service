package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class DepositServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val transactionExecutor: TransactionExecutor = mockk()
    private val service = DepositService(accountRepository, ledgerEntryRepository, transactionExecutor)

    @Test
    fun `입금 성공 및 원장 엔트리 생성`() = runTest {
        // given
        val accountId = 1L
        val initialBalance = BigDecimal("500.00")
        val depositAmount = BigDecimal("300.00")
        val description = "Deposit"

        val account = Account(
            id = accountId,
            ownerName = "John",
            balance = initialBalance,
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val savedAccount = Account(
            id = accountId,
            ownerName = "John",
            balance = BigDecimal("800.00"),
            status = AccountStatus.ACTIVE,
            version = 1L
        )

        // TransactionExecutor 모킹: 람다를 즉시 실행
        coEvery { transactionExecutor.execute<Account>(any()) } coAnswers {
            firstArg<suspend () -> Account>().invoke()
        }

        coEvery { accountRepository.findByIdForUpdate(accountId) } returns account
        coEvery { accountRepository.save(any()) } returns savedAccount
        coEvery { ledgerEntryRepository.save(any()) } returns mockk()

        // when
        val result = service.execute(accountId, depositAmount, description)

        // then
        assert(result.balance == BigDecimal("800.00"))
        assert(result.version == 1L)

        coVerify(exactly = 1) { accountRepository.findByIdForUpdate(accountId) }
        coVerify(exactly = 1) { accountRepository.save(any()) }
        coVerify(exactly = 1) { ledgerEntryRepository.save(any()) }
    }

    @Test
    fun `계좌 미존재시 AccountNotFoundException 발생`() = runTest {
        // given
        val accountId = 999L
        val amount = BigDecimal("100.00")

        coEvery { transactionExecutor.execute<Account>(any()) } coAnswers {
            firstArg<suspend () -> Account>().invoke()
        }
        coEvery { accountRepository.findByIdForUpdate(accountId) } returns null

        // when & then
        assertThrows<AccountNotFoundException> {
            service.execute(accountId, amount, null)
        }
    }

    @Test
    fun `원장 엔트리 필드 검증`() = runTest {
        // given
        val accountId = 1L
        val amount = BigDecimal("100.00")
        val description = "Test deposit"
        val account = Account(
            id = accountId,
            ownerName = "John",
            balance = BigDecimal.ZERO,
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val savedAccount = Account(
            id = accountId,
            ownerName = "John",
            balance = amount,
            status = AccountStatus.ACTIVE,
            version = 1L
        )

        val ledgerSlot = slot<LedgerEntry>()

        coEvery { transactionExecutor.execute<Account>(any()) } coAnswers {
            firstArg<suspend () -> Account>().invoke()
        }
        coEvery { accountRepository.findByIdForUpdate(accountId) } returns account
        coEvery { accountRepository.save(any()) } returns savedAccount
        coEvery { ledgerEntryRepository.save(capture(ledgerSlot)) } returns mockk()

        // when
        service.execute(accountId, amount, description)

        // then
        val ledger = ledgerSlot.captured
        assert(ledger.accountId == accountId)
        assert(ledger.type == LedgerEntryType.CREDIT)
        assert(ledger.amount == amount)
        assert(ledger.description == description)
    }
}
