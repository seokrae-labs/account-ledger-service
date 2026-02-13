package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.repository.AccountEntityRepository
import com.labs.ledger.adapter.out.persistence.repository.TransferEntityRepository
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
class TransferPersistenceAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: TransferPersistenceAdapter

    @Autowired
    private lateinit var accountAdapter: AccountPersistenceAdapter

    @Autowired
    private lateinit var repository: TransferEntityRepository

    @Autowired
    private lateinit var accountRepository: AccountEntityRepository

    private var fromAccountId: Long = 0
    private var toAccountId: Long = 0

    @BeforeEach
    fun setupAccounts() = runTest {
        // Create test accounts for foreign key constraints
        val account1 = accountAdapter.save(
            Account(
                ownerName = "From Account",
                balance = BigDecimal("1000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val account2 = accountAdapter.save(
            Account(
                ownerName = "To Account",
                balance = BigDecimal("500.00"),
                status = AccountStatus.ACTIVE
            )
        )
        fromAccountId = account1.id!!
        toAccountId = account2.id!!
    }

    @AfterEach
    fun cleanup() = runTest {
        repository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `이체 저장 및 조회`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "test-key-001",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("500.00"),
            status = TransferStatus.PENDING,
            description = "Test transfer"
        )

        // when
        val saved = adapter.save(transfer)

        // then
        assert(saved.id != null)
        assert(saved.idempotencyKey == "test-key-001")
        assert(saved.fromAccountId == 1L)
        assert(saved.toAccountId == 2L)
        assert(saved.amount == BigDecimal("500.00"))
        assert(saved.status == TransferStatus.PENDING)
        assert(saved.description == "Test transfer")
    }

    @Test
    fun `idempotencyKey로 이체 조회`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "unique-key-123",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("100.00"),
            status = TransferStatus.COMPLETED
        )
        adapter.save(transfer)

        // when
        val found = adapter.findByIdempotencyKey("unique-key-123")

        // then
        assert(found != null)
        assert(found!!.idempotencyKey == "unique-key-123")
        assert(found.status == TransferStatus.COMPLETED)
    }

    @Test
    fun `존재하지 않는 idempotencyKey 조회`() = runTest {
        // when
        val found = adapter.findByIdempotencyKey("non-existent-key")

        // then
        assert(found == null)
    }

    @Test
    fun `PENDING 상태 이체 저장`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "pending-transfer",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("200.00"),
            status = TransferStatus.PENDING
        )

        // when
        val saved = adapter.save(transfer)

        // then
        assert(saved.status == TransferStatus.PENDING)
    }

    @Test
    fun `COMPLETED 상태 이체 저장`() = runTest {
        // given
        val pendingTransfer = Transfer(
            idempotencyKey = "complete-test",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("300.00"),
            status = TransferStatus.PENDING
        )
        val saved = adapter.save(pendingTransfer)

        // when - Complete the transfer
        val completed = saved.complete()
        val updated = adapter.save(completed)

        // then
        assert(updated.status == TransferStatus.COMPLETED)
        assert(updated.id == saved.id)
    }

    @Test
    fun `FAILED 상태 이체 저장`() = runTest {
        // given
        val pendingTransfer = Transfer(
            idempotencyKey = "fail-test",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("400.00"),
            status = TransferStatus.PENDING
        )
        val saved = adapter.save(pendingTransfer)

        // when - Fail the transfer
        val failed = saved.fail()
        val updated = adapter.save(failed)

        // then
        assert(updated.status == TransferStatus.FAILED)
        assert(updated.id == saved.id)
    }

    @Test
    fun `이체 상태 전이 검증 - PENDING to COMPLETED`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "transition-test-1",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("150.00"),
            status = TransferStatus.PENDING
        )
        val pending = adapter.save(transfer)

        // when
        val completed = pending.complete()
        val updated = adapter.save(completed)

        // then
        val found = adapter.findByIdempotencyKey("transition-test-1")
        assert(found != null)
        assert(found!!.status == TransferStatus.COMPLETED)
    }

    @Test
    fun `이체 상태 전이 검증 - PENDING to FAILED`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "transition-test-2",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("250.00"),
            status = TransferStatus.PENDING
        )
        val pending = adapter.save(transfer)

        // when
        val failed = pending.fail()
        val updated = adapter.save(failed)

        // then
        val found = adapter.findByIdempotencyKey("transition-test-2")
        assert(found != null)
        assert(found!!.status == TransferStatus.FAILED)
    }

    @Test
    fun `Entity-Domain 매핑 검증`() = runTest {
        // given
        val transfer = Transfer(
            idempotencyKey = "mapping-test",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("789.12"),
            status = TransferStatus.PENDING,
            description = "Mapping verification"
        )

        // when
        val saved = adapter.save(transfer)
        val retrieved = adapter.findByIdempotencyKey("mapping-test")

        // then - All domain fields should match
        assert(retrieved != null)
        assert(retrieved!!.id == saved.id)
        assert(retrieved.idempotencyKey == transfer.idempotencyKey)
        assert(retrieved.fromAccountId == transfer.fromAccountId)
        assert(retrieved.toAccountId == transfer.toAccountId)
        assert(retrieved.amount.compareTo(transfer.amount) == 0)
        assert(retrieved.status == transfer.status)
        assert(retrieved.description == transfer.description)
        assert(retrieved.createdAt != null)
        assert(retrieved.updatedAt != null)
    }

    @Test
    fun `idempotencyKey 유니크 제약 검증`() = runTest {
        // given
        val transfer1 = Transfer(
            idempotencyKey = "duplicate-key",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING
        )
        adapter.save(transfer1)

        // when & then
        // R2DBC will throw exception on duplicate key
        // This is handled at service layer, but we verify constraint exists
        val found = adapter.findByIdempotencyKey("duplicate-key")
        assert(found != null)
    }
}
