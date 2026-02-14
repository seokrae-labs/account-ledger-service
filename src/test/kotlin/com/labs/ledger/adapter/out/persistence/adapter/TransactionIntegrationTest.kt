package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.repository.AccountEntityRepository
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class TransactionIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var transactionExecutor: R2dbcTransactionExecutor

    @Autowired
    private lateinit var accountAdapter: AccountPersistenceAdapter

    @Autowired
    private lateinit var repository: AccountEntityRepository

    @Test
    fun `트랜잭션 성공 시 커밋`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Transaction Test",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE
        )

        // when
        val saved = transactionExecutor.execute {
            accountAdapter.save(account)
        }

        // then
        assert(saved.id != null)
        val found = accountAdapter.findById(saved.id!!)
        assert(found != null)
        assert(found!!.ownerName == "Transaction Test")
    }

    @Test
    fun `트랜잭션 예외 발생 시 롤백`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Rollback Test",
            balance = BigDecimal("500.00"),
            status = AccountStatus.ACTIVE
        )

        // when & then
        assertThrows<RuntimeException> {
            transactionExecutor.execute {
                accountAdapter.save(account)
                throw RuntimeException("Simulated failure")
            }
        }

        // Verify rollback - account should not exist
        val allAccounts = repository.findAll().toList()
        assert(allAccounts.isEmpty()) { "Expected no accounts after rollback, found ${allAccounts.size}" }
    }

    @Test
    fun `트랜잭션 내 여러 연산 원자성 보장`() = runBlocking {
        // given
        val account1 = Account(
            ownerName = "User1",
            balance = BigDecimal("100.00"),
            status = AccountStatus.ACTIVE
        )
        val account2 = Account(
            ownerName = "User2",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE
        )

        // when & then
        assertThrows<RuntimeException> {
            transactionExecutor.execute {
                accountAdapter.save(account1)
                accountAdapter.save(account2)
                // Fail after both saves
                throw RuntimeException("Atomic failure")
            }
        }

        // Verify both operations rolled back
        val allAccounts = repository.findAll().toList()
        assert(allAccounts.isEmpty()) { "Expected no accounts after atomic rollback" }
    }

    @Test
    fun `트랜잭션 부분 성공 시 롤백`() = runBlocking {
        // given
        val account1 = Account(
            ownerName = "Partial1",
            balance = BigDecimal("300.00"),
            status = AccountStatus.ACTIVE
        )
        val account2 = Account(
            ownerName = "Partial2",
            balance = BigDecimal("400.00"),
            status = AccountStatus.ACTIVE
        )

        // Save first account successfully
        val saved1 = transactionExecutor.execute {
            accountAdapter.save(account1)
        }

        // when & then - Fail on second account
        assertThrows<RuntimeException> {
            transactionExecutor.execute {
                accountAdapter.save(account2)
                throw RuntimeException("Second transaction fails")
            }
        }

        // Verify only first account exists
        val allAccounts = repository.findAll().toList()
        assert(allAccounts.size == 1) { "Expected 1 account, found ${allAccounts.size}" }
        assert(allAccounts[0].ownerName == "Partial1")
    }

    @Test
    fun `중첩 트랜잭션 동작 확인`() = runBlocking {
        // given
        val account = Account(
            ownerName = "Nested Test",
            balance = BigDecimal("600.00"),
            status = AccountStatus.ACTIVE
        )

        // when
        val saved = transactionExecutor.execute {
            val inner = accountAdapter.save(account)

            // Update within same transaction
            val updated = inner.copy(balance = BigDecimal("700.00"))
            accountAdapter.save(updated)
        }

        // then
        assert(saved.balance.compareTo(BigDecimal("700.00")) == 0)
        val found = accountAdapter.findById(saved.id!!)
        assert(found!!.balance.compareTo(BigDecimal("700.00")) == 0)
    }

    @Test
    fun `트랜잭션 내 조회 및 업데이트 일관성`() = runBlocking {
        // given
        val account = transactionExecutor.execute {
            accountAdapter.save(
                Account(
                    ownerName = "Consistency Test",
                    balance = BigDecimal("1000.00"),
                    status = AccountStatus.ACTIVE
                )
            )
        }

        // when
        val updated = transactionExecutor.execute {
            val found = accountAdapter.findByIdForUpdate(account.id!!)
            val modified = found!!.copy(balance = BigDecimal("1500.00"))
            accountAdapter.save(modified)
        }

        // then
        assert(updated.balance.compareTo(BigDecimal("1500.00")) == 0)
        val final = accountAdapter.findById(account.id!!)
        assert(final!!.balance.compareTo(BigDecimal("1500.00")) == 0)
    }

    @Test
    fun `여러 트랜잭션 독립성 보장`() = runBlocking {
        // Transaction 1
        val account1 = transactionExecutor.execute {
            accountAdapter.save(
                Account(
                    ownerName = "Independent1",
                    balance = BigDecimal("100.00"),
                    status = AccountStatus.ACTIVE
                )
            )
        }

        // Transaction 2 (independent)
        val account2 = transactionExecutor.execute {
            accountAdapter.save(
                Account(
                    ownerName = "Independent2",
                    balance = BigDecimal("200.00"),
                    status = AccountStatus.ACTIVE
                )
            )
        }

        // Verify both exist independently
        val found1 = accountAdapter.findById(account1.id!!)
        val found2 = accountAdapter.findById(account2.id!!)

        assert(found1 != null)
        assert(found2 != null)
        assert(found1!!.balance.compareTo(BigDecimal("100.00")) == 0)
        assert(found2!!.balance.compareTo(BigDecimal("200.00")) == 0)
    }
}
