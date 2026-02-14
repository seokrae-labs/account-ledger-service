package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.repository.AccountEntityRepository
import com.labs.ledger.adapter.out.persistence.repository.LedgerEntryEntityRepository
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Sql(
    scripts = ["/schema-reset.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class LedgerEntryPersistenceAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: LedgerEntryPersistenceAdapter

    @Autowired
    private lateinit var accountAdapter: AccountPersistenceAdapter

    @Autowired
    private lateinit var repository: LedgerEntryEntityRepository

    @Autowired
    private lateinit var accountRepository: AccountEntityRepository

    private lateinit var testAccount: Account

    @BeforeEach
    fun setup() = runBlocking {
        // Create test account
        testAccount = accountAdapter.save(
            Account(
                ownerName = "Test User",
                balance = BigDecimal("1000.00"),
                status = AccountStatus.ACTIVE
            )
        )
    }

    @Test
    fun `원장 엔트리 저장 및 조회`() = runBlocking {
        // given
        val entry = LedgerEntry(
            accountId = testAccount.id!!,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("500.00"),
            referenceId = "test-ref-001",
            description = "Test deposit"
        )

        // when
        val saved = adapter.save(entry)

        // then
        assert(saved.id != null)
        assert(saved.accountId == testAccount.id)
        assert(saved.type == LedgerEntryType.CREDIT)
        assert(saved.amount.compareTo(BigDecimal("500.00")) == 0)
        assert(saved.referenceId == "test-ref-001")
        assert(saved.description == "Test deposit")
    }

    @Test
    fun `여러 원장 엔트리 일괄 저장`() = runBlocking {
        // given
        val entries = listOf(
            LedgerEntry(
                accountId = testAccount.id!!,
                type = LedgerEntryType.DEBIT,
                amount = BigDecimal("100.00"),
                referenceId = "batch-001",
                description = "Batch 1"
            ),
            LedgerEntry(
                accountId = testAccount.id!!,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("200.00"),
                referenceId = "batch-002",
                description = "Batch 2"
            )
        )

        // when
        val saved = adapter.saveAll(entries)

        // then
        assert(saved.size == 2)
        assert(saved.all { it.id != null })
        assert(saved[0].type == LedgerEntryType.DEBIT)
        assert(saved[1].type == LedgerEntryType.CREDIT)
    }

    @Test
    fun `계좌별 원장 엔트리 조회`() = runBlocking {
        // given
        val account1 = accountAdapter.save(
            Account(
                ownerName = "User 1",
                balance = BigDecimal.ZERO,
                status = AccountStatus.ACTIVE
            )
        )
        val account2 = accountAdapter.save(
            Account(
                ownerName = "User 2",
                balance = BigDecimal.ZERO,
                status = AccountStatus.ACTIVE
            )
        )

        // Save entries for account1
        adapter.save(
            LedgerEntry(
                accountId = account1.id!!,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("100.00")
            )
        )
        adapter.save(
            LedgerEntry(
                accountId = account1.id!!,
                type = LedgerEntryType.DEBIT,
                amount = BigDecimal("50.00")
            )
        )

        // Save entry for account2
        adapter.save(
            LedgerEntry(
                accountId = account2.id!!,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("300.00")
            )
        )

        // when
        val account1Entries = adapter.findByAccountId(account1.id!!)
        val account2Entries = adapter.findByAccountId(account2.id!!)

        // then
        assert(account1Entries.size == 2)
        assert(account2Entries.size == 1)
        assert(account1Entries.all { it.accountId == account1.id })
        assert(account2Entries.all { it.accountId == account2.id })
    }

    @Test
    fun `원장 엔트리 페이징 조회 및 카운트`() = runBlocking {
        // given
        repeat(3) { idx ->
            adapter.save(
                LedgerEntry(
                    accountId = testAccount.id!!,
                    type = if (idx % 2 == 0) LedgerEntryType.CREDIT else LedgerEntryType.DEBIT,
                    amount = BigDecimal("10.00").plus(BigDecimal(idx)),
                    referenceId = "page-test-$idx",
                    description = "Entry $idx"
                )
            )
        }

        // when
        val firstPage = adapter.findByAccountId(testAccount.id!!, offset = 0, limit = 2)
        val secondPage = adapter.findByAccountId(testAccount.id!!, offset = 2, limit = 2)
        val totalCount = adapter.countByAccountId(testAccount.id!!)

        // then
        assert(firstPage.size == 2)
        assert(secondPage.size == 1)
        assert(totalCount == 3L)
        assert((firstPage + secondPage).all { it.accountId == testAccount.id })
    }

    @Test
    fun `DEBIT 및 CREDIT 타입 검증`() = runBlocking {
        // given
        val debitEntry = LedgerEntry(
            accountId = testAccount.id!!,
            type = LedgerEntryType.DEBIT,
            amount = BigDecimal("50.00"),
            description = "Withdrawal"
        )
        val creditEntry = LedgerEntry(
            accountId = testAccount.id!!,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("100.00"),
            description = "Deposit"
        )

        // when
        val savedDebit = adapter.save(debitEntry)
        val savedCredit = adapter.save(creditEntry)

        // then
        assert(savedDebit.type == LedgerEntryType.DEBIT)
        assert(savedCredit.type == LedgerEntryType.CREDIT)

        val entries = adapter.findByAccountId(testAccount.id!!)
        assert(entries.any { it.type == LedgerEntryType.DEBIT })
        assert(entries.any { it.type == LedgerEntryType.CREDIT })
    }

    @Test
    fun `Entity-Domain 매핑 검증`() = runBlocking {
        // given
        val entry = LedgerEntry(
            accountId = testAccount.id!!,
            type = LedgerEntryType.CREDIT,
            amount = BigDecimal("123.45"),
            referenceId = "mapping-test",
            description = "Mapping verification"
        )

        // when
        val saved = adapter.save(entry)
        val retrieved = adapter.findByAccountId(testAccount.id!!).first()

        // then - All domain fields should match
        assert(retrieved.id == saved.id)
        assert(retrieved.accountId == entry.accountId)
        assert(retrieved.type == entry.type)
        assert(retrieved.amount.compareTo(entry.amount) == 0)
        assert(retrieved.referenceId == entry.referenceId)
        assert(retrieved.description == entry.description)
        assert(retrieved.createdAt != null)
    }

    @Test
    fun `빈 결과 조회`() = runBlocking {
        // given
        val nonExistentAccountId = 999L

        // when
        val entries = adapter.findByAccountId(nonExistentAccountId)

        // then
        assert(entries.isEmpty())
    }
}
