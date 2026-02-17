package com.labs.ledger.application.service

import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GetAccountsServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val service = GetAccountsService(accountRepository)

    private fun account(id: Long) = Account(
        id = id,
        ownerName = "User $id",
        balance = BigDecimal("1000.00"),
        status = AccountStatus.ACTIVE
    )

    @Test
    fun `결과가 0건일 때 빈 리스트와 totalElements 0을 반환한다`() = runTest {
        // given
        coEvery { accountRepository.findAll(0L, 10) } returns emptyList()
        coEvery { accountRepository.count() } returns 0L

        // when
        val result = service.execute(page = 0, size = 10)

        // then
        assert(result.accounts.isEmpty())
        assert(result.totalElements == 0L)
        assert(result.page == 0)
        assert(result.size == 10)
    }

    @Test
    fun `첫 번째 페이지 조회 시 offset 0으로 repository를 호출한다`() = runTest {
        // given
        val accounts = listOf(account(1L), account(2L))
        coEvery { accountRepository.findAll(0L, 10) } returns accounts
        coEvery { accountRepository.count() } returns 2L

        // when
        val result = service.execute(page = 0, size = 10)

        // then
        assert(result.accounts.size == 2)
        assert(result.totalElements == 2L)
        assert(result.page == 0)
        coVerify(exactly = 1) { accountRepository.findAll(0L, 10) }
    }

    @Test
    fun `두 번째 페이지 조회 시 offset이 page * size로 계산된다`() = runTest {
        // given: page=1, size=10 → offset=10
        val accounts = listOf(account(11L))
        coEvery { accountRepository.findAll(10L, 10) } returns accounts
        coEvery { accountRepository.count() } returns 11L

        // when
        val result = service.execute(page = 1, size = 10)

        // then
        assert(result.page == 1)
        assert(result.accounts.size == 1)
        coVerify(exactly = 1) { accountRepository.findAll(10L, 10) }
    }

    @Test
    fun `size가 1일 때 페이지당 하나의 결과만 반환한다`() = runTest {
        // given
        val accounts = listOf(account(1L))
        coEvery { accountRepository.findAll(0L, 1) } returns accounts
        coEvery { accountRepository.count() } returns 5L

        // when
        val result = service.execute(page = 0, size = 1)

        // then
        assert(result.accounts.size == 1)
        assert(result.totalElements == 5L)
        coVerify(exactly = 1) { accountRepository.findAll(0L, 1) }
    }

    @Test
    fun `큰 페이지 번호 조회 시 offset이 Long 범위 내에서 올바르게 계산된다`() = runTest {
        // given: page=1000, size=100 → offset=100_000 (Int overflow 방지)
        val expectedOffset = 100_000L
        coEvery { accountRepository.findAll(expectedOffset, 100) } returns emptyList()
        coEvery { accountRepository.count() } returns 0L

        // when
        val result = service.execute(page = 1000, size = 100)

        // then
        assert(result.accounts.isEmpty())
        coVerify(exactly = 1) { accountRepository.findAll(expectedOffset, 100) }
    }

    @Test
    fun `totalElements와 반환된 accounts 수가 독립적이다`() = runTest {
        // given: DB에 총 50개지만 현재 페이지엔 3개만 존재
        val accounts = listOf(account(1L), account(2L), account(3L))
        coEvery { accountRepository.findAll(0L, 10) } returns accounts
        coEvery { accountRepository.count() } returns 50L

        // when
        val result = service.execute(page = 0, size = 10)

        // then: accounts 수(3)와 totalElements(50)는 독립
        assert(result.accounts.size == 3)
        assert(result.totalElements == 50L)
        coVerify(exactly = 1) { accountRepository.findAll(0L, 10) }
        coVerify(exactly = 1) { accountRepository.count() }
    }
}
