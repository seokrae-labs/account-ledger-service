package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.repository.AccountEntityRepository
import com.labs.ledger.domain.exception.OptimisticLockException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class AccountPersistenceAdapterIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var adapter: AccountPersistenceAdapter

    @Autowired
    private lateinit var repository: AccountEntityRepository

    @Test
    fun `계좌 저장 및 조회`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Alice",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE
        )

        // when
        val saved = adapter.save(account)

        // then
        assert(saved.id != null)
        assert(saved.ownerName == "Alice")
        assert(saved.balance.compareTo(BigDecimal("1000.00")) == 0)
        assert(saved.status == AccountStatus.ACTIVE)
        assert(saved.version >= 0L) { "Expected version >= 0, but got ${saved.version}" }

        // Verify findById
        val found = adapter.findById(saved.id!!)
        assert(found != null)
        assert(found!!.id == saved.id)
        assert(found.ownerName == saved.ownerName)
    }

    @Test
    fun `계좌 업데이트 및 버전 증가`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Bob",
            balance = BigDecimal("500.00"),
            status = AccountStatus.ACTIVE
        )
        val saved = adapter.save(account)

        // when
        val updated = saved.copy(balance = BigDecimal("600.00"))
        val result = adapter.save(updated)

        // then
        assert(result.id == saved.id)
        assert(result.balance.compareTo(BigDecimal("600.00")) == 0)
        assert(result.version > saved.version) { "Expected version > ${saved.version}, got ${result.version}" }
    }

    @Test
    fun `Optimistic Lock 충돌 감지`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Charlie",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE
        )
        val saved = adapter.save(account)

        // Simulate concurrent modification (update version in DB)
        val firstUpdate = saved.copy(balance = BigDecimal("1100.00"))
        adapter.save(firstUpdate)

        // when & then
        // Try to save with old version (stale data)
        val staleUpdate = saved.copy(balance = BigDecimal("900.00"))
        assertThrows<OptimisticLockException> {
            adapter.save(staleUpdate)
        }
    }

    @Test
    fun `findByIdForUpdate - 잠금 획득`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Dave",
            balance = BigDecimal("800.00"),
            status = AccountStatus.ACTIVE
        )
        val saved = adapter.save(account)

        // when
        val locked = adapter.findByIdForUpdate(saved.id!!)

        // then
        assert(locked != null)
        assert(locked!!.id == saved.id)
        assert(locked.balance.compareTo(BigDecimal("800.00")) == 0)
    }

    @Test
    fun `findByIdsForUpdate - 정렬된 순서로 조회`() = runBlocking {
        // given
        val account1 = adapter.save(
            Account(
                ownerName = "User1",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val account2 = adapter.save(
            Account(
                ownerName = "User2",
                balance = BigDecimal("200.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val account3 = adapter.save(
            Account(
                ownerName = "User3",
                balance = BigDecimal("300.00"),
                status = AccountStatus.ACTIVE
            )
        )

        // when - 역순으로 요청
        val ids = listOf(account3.id!!, account1.id!!, account2.id!!)
        val accounts = adapter.findByIdsForUpdate(ids)

        // then - 정렬된 순서로 반환되어야 함
        assert(accounts.size == 3)
        assert(accounts[0].id == account1.id)
        assert(accounts[1].id == account2.id)
        assert(accounts[2].id == account3.id)
    }

    @Test
    fun `findById - 존재하지 않는 계좌`() = runBlocking {
        // when
        val result = adapter.findById(999L)

        // then
        assert(result == null)
    }

    @Test
    fun `Entity-Domain 매핑 검증`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Mapping Test",
            balance = BigDecimal("123.45"),
            status = AccountStatus.ACTIVE
        )

        // when
        val saved = adapter.save(account)
        val retrieved = adapter.findById(saved.id!!)

        // then - All domain fields should match
        assert(retrieved != null)
        assert(retrieved!!.ownerName == account.ownerName)
        assert(retrieved.balance.compareTo(account.balance) == 0)
        assert(retrieved.status == account.status)
        assert(retrieved.version >= 0L)
        assert(retrieved.createdAt != null)
        assert(retrieved.updatedAt != null)
    }
}
