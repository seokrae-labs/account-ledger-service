package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDateTime

class GetLedgerEntriesServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val service = GetLedgerEntriesService(accountRepository, ledgerEntryRepository)

    @Test
    fun `페이지네이션된 원장 조회 성공`() = runTest {
        // given
        val accountId = 1L
        val page = 0
        val size = 10
        val offset = 0L

        val account = Account(
            id = accountId,
            ownerName = "Test User",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE
        )

        val entries = listOf(
            LedgerEntry(
                id = 1L,
                accountId = accountId,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("100.00"),
                referenceId = "ref1",
                description = "Deposit",
                createdAt = LocalDateTime.now()
            ),
            LedgerEntry(
                id = 2L,
                accountId = accountId,
                type = LedgerEntryType.DEBIT,
                amount = BigDecimal("50.00"),
                referenceId = "ref2",
                description = "Withdrawal",
                createdAt = LocalDateTime.now()
            )
        )

        coEvery { accountRepository.findById(accountId) } returns account
        coEvery { ledgerEntryRepository.findByAccountId(accountId, offset, size) } returns entries
        coEvery { ledgerEntryRepository.countByAccountId(accountId) } returns 25L

        // when
        val result = service.execute(accountId, page, size)

        // then
        assert(result.entries.size == 2)
        assert(result.totalElements == 25L)
        assert(result.page == 0)
        assert(result.size == 10)

        coVerify { accountRepository.findById(accountId) }
        coVerify { ledgerEntryRepository.findByAccountId(accountId, offset, size) }
        coVerify { ledgerEntryRepository.countByAccountId(accountId) }
    }

    @Test
    fun `존재하지 않는 계좌로 조회 시 예외 발생`() = runTest {
        // given
        val accountId = 999L
        coEvery { accountRepository.findById(accountId) } returns null

        // when & then
        assertThrows<AccountNotFoundException> {
            service.execute(accountId, 0, 10)
        }
    }

    @Test
    fun `두 번째 페이지 조회`() = runTest {
        // given
        val accountId = 1L
        val page = 1
        val size = 10
        val offset = 10L

        val account = Account(
            id = accountId,
            ownerName = "Test User",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE
        )

        val entries = listOf(
            LedgerEntry(
                id = 11L,
                accountId = accountId,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("100.00"),
                referenceId = "ref11",
                description = "Deposit",
                createdAt = LocalDateTime.now()
            )
        )

        coEvery { accountRepository.findById(accountId) } returns account
        coEvery { ledgerEntryRepository.findByAccountId(accountId, offset, size) } returns entries
        coEvery { ledgerEntryRepository.countByAccountId(accountId) } returns 25L

        // when
        val result = service.execute(accountId, page, size)

        // then
        assert(result.page == 1)
        assert(result.entries.size == 1)
        coVerify { ledgerEntryRepository.findByAccountId(accountId, offset, size) }
    }

    @Test
    fun `빈 결과 조회`() = runTest {
        // given
        val accountId = 1L
        val page = 0
        val size = 10

        val account = Account(
            id = accountId,
            ownerName = "Test User",
            balance = BigDecimal("0.00"),
            status = AccountStatus.ACTIVE
        )

        coEvery { accountRepository.findById(accountId) } returns account
        coEvery { ledgerEntryRepository.findByAccountId(accountId, 0L, size) } returns emptyList()
        coEvery { ledgerEntryRepository.countByAccountId(accountId) } returns 0L

        // when
        val result = service.execute(accountId, page, size)

        // then
        assert(result.entries.isEmpty())
        assert(result.totalElements == 0L)
    }
}
