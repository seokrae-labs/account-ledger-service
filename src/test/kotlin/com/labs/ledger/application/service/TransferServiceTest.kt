package com.labs.ledger.application.service

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.TransferAuditRepository
import com.labs.ledger.domain.port.TransferRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class TransferServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val transferRepository: TransferRepository = mockk()
    private val transactionExecutor: TransactionExecutor = mockk()
    private val transferAuditRepository: TransferAuditRepository = mockk()
    private val service = TransferService(
        accountRepository,
        ledgerEntryRepository,
        transferRepository,
        transactionExecutor,
        transferAuditRepository
    )

    @Test
    fun `이체 성공 - 전체 플로우`() = runTest {
        // given
        val idempotencyKey = "test-key-001"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("500.00")

        val fromAccount = Account(
            id = fromAccountId,
            ownerName = "Alice",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = null
        )
        val completedTransfer = pendingTransfer.complete()

        var findByIdempotencyKeyCallCount = 0

        // Fast path: no existing transfer
        // Double-check inside transaction: also null
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findByIdempotencyKeyCallCount++
            null
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen completedTransfer

        // Accounts loaded in sorted order
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount, toAccount)
        coEvery { accountRepository.save(any()) } returns mockk()

        // Ledger entries
        coEvery { ledgerEntryRepository.saveAll(any()) } returns mockk()

        // Audit event
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when
        val result = service.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)

        // then
        assert(result.status == TransferStatus.COMPLETED)
        assert(result.amount == amount)

        coVerify(exactly = 1) { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) }
        coVerify(exactly = 2) { accountRepository.save(any()) }
        coVerify(exactly = 1) { ledgerEntryRepository.saveAll(any()) }
        coVerify(exactly = 2) { transferRepository.save(any()) }
        coVerify(exactly = 1) { transferAuditRepository.save(any()) }
    }

    @Test
    fun `멱등성 fast path - COMPLETED 반환`() = runTest {
        // given
        val idempotencyKey = "duplicate-key"
        val completedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.COMPLETED,
            description = null
        )

        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns completedTransfer

        // when
        val result = service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)

        // then
        assert(result == completedTransfer)

        // No transaction should be executed
        coVerify(exactly = 0) { transactionExecutor.execute<Transfer>(any()) }
    }

    @Test
    fun `멱등성 fast path - PENDING 상태시 DuplicateTransferException`() = runTest {
        // given
        val idempotencyKey = "pending-key"
        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING,
            description = null
        )

        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns pendingTransfer

        // when & then
        assertThrows<DuplicateTransferException> {
            service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)
        }
    }

    @Test
    fun `멱등성 double-check - COMPLETED (race condition)`() = runTest {
        // given
        val idempotencyKey = "race-key-completed"
        val completedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.COMPLETED,
            description = null
        )

        var findCallCount = 0

        // Fast path: null (no existing transfer)
        // Double-check inside transaction: COMPLETED (created by another request)
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            if (findCallCount == 1) null else completedTransfer
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        // when
        val result = service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)

        // then
        assert(result == completedTransfer)

        // Should not proceed with transfer creation
        coVerify(exactly = 0) { transferRepository.save(any()) }
        coVerify(exactly = 0) { accountRepository.findByIdsForUpdate(any()) }
    }

    @Test
    fun `멱등성 double-check - PENDING (race condition)`() = runTest {
        // given
        val idempotencyKey = "race-key-pending"
        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING,
            description = null
        )

        var findCallCount = 0

        // Fast path: null
        // Double-check: PENDING (created by concurrent request)
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            if (findCallCount == 1) null else pendingTransfer
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        // when & then
        assertThrows<DuplicateTransferException> {
            service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)
        }
    }

    @Test
    fun `출금 계좌 미존재시 AccountNotFoundException`() = runTest {
        // given
        val idempotencyKey = "key-no-from"
        val toAccount = Account(
            id = 2L,
            ownerName = "Bob",
            balance = BigDecimal.ZERO,
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING,
            description = null
        )

        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(toAccount)

        // when & then
        assertThrows<AccountNotFoundException> {
            service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)
        }
    }

    @Test
    fun `입금 계좌 미존재시 AccountNotFoundException`() = runTest {
        // given
        val idempotencyKey = "key-no-to"
        val fromAccount = Account(
            id = 1L,
            ownerName = "Alice",
            balance = BigDecimal("500.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.PENDING,
            description = null
        )

        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount)

        // when & then
        assertThrows<AccountNotFoundException> {
            service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)
        }
    }

    @Test
    fun `이체 성공 - description 포함`() = runTest {
        // given
        val idempotencyKey = "test-key-with-desc"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("300.00")
        val description = "Monthly payment"

        val fromAccount = Account(
            id = fromAccountId,
            ownerName = "Alice",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = description
        )
        val completedTransfer = pendingTransfer.complete()

        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen completedTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount, toAccount)
        coEvery { accountRepository.save(any()) } returns mockk()
        coEvery { ledgerEntryRepository.saveAll(any()) } returns mockk()
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when
        val result = service.execute(idempotencyKey, fromAccountId, toAccountId, amount, description)

        // then
        assert(result.status == TransferStatus.COMPLETED)
        assert(result.description == description)
    }

    @Test
    fun `이체 시 원장 엔트리 생성 검증`() = runTest {
        // given
        val idempotencyKey = "ledger-test"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("200.00")

        val fromAccount = Account(
            id = fromAccountId,
            ownerName = "Alice",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("500.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = null
        )
        val completedTransfer = pendingTransfer.complete()

        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen completedTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount, toAccount)
        coEvery { accountRepository.save(any()) } returns mockk()

        val ledgerEntriesSlot = slot<List<LedgerEntry>>()
        coEvery { ledgerEntryRepository.saveAll(capture(ledgerEntriesSlot)) } returns mockk()
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when
        service.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)

        // then
        val ledgerEntries = ledgerEntriesSlot.captured
        assert(ledgerEntries.size == 2)

        // Debit entry
        val debitEntry = ledgerEntries.find { it.type == LedgerEntryType.DEBIT }!!
        assert(debitEntry.accountId == fromAccountId)
        assert(debitEntry.amount == amount)
        assert(debitEntry.referenceId == idempotencyKey)

        // Credit entry
        val creditEntry = ledgerEntries.find { it.type == LedgerEntryType.CREDIT }!!
        assert(creditEntry.accountId == toAccountId)
        assert(creditEntry.amount == amount)
        assert(creditEntry.referenceId == idempotencyKey)
    }

    @Test
    fun `멱등성 fast path - FAILED 상태시 기존 FAILED 반환`() = runTest {
        // given
        val idempotencyKey = "failed-key"
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            description = null
        )

        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns failedTransfer

        // when
        val result = service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)

        // then
        assert(result == failedTransfer)

        // No transaction should be executed
        coVerify(exactly = 0) { transactionExecutor.execute<Transfer>(any()) }
    }

    @Test
    fun `멱등성 double-check - FAILED 상태시 기존 FAILED 반환 (race condition)`() = runTest {
        // given
        val idempotencyKey = "race-key-failed"
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            description = null
        )

        var findCallCount = 0

        // Fast path: null (no existing transfer)
        // Double-check inside transaction: FAILED (created by another request)
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            if (findCallCount == 1) null else failedTransfer
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        // when
        val result = service.execute(idempotencyKey, 1L, 2L, BigDecimal("100.00"), null)

        // then
        assert(result == failedTransfer)

        // Should not proceed with transfer creation
        coVerify(exactly = 0) { transferRepository.save(any()) }
        coVerify(exactly = 0) { accountRepository.findByIdsForUpdate(any()) }
    }

    @Test
    fun `Deadlock prevention - 계좌 ID 정렬 검증`() = runTest {
        // given
        val idempotencyKey = "deadlock-test"
        val fromAccountId = 5L // 큰 ID
        val toAccountId = 2L   // 작은 ID
        val amount = BigDecimal("100.00")

        val fromAccount = Account(
            id = fromAccountId,
            ownerName = "Alice",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = null
        )
        val completedTransfer = pendingTransfer.complete()

        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen completedTransfer

        // 정렬된 순서로 호출되어야 함: [2, 5]
        val sortedIdsSlot = slot<List<Long>>()
        coEvery { accountRepository.findByIdsForUpdate(capture(sortedIdsSlot)) } returns listOf(toAccount, fromAccount)
        coEvery { accountRepository.save(any()) } returns mockk()
        coEvery { ledgerEntryRepository.saveAll(any()) } returns mockk()
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when
        service.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)

        // then
        assert(sortedIdsSlot.captured == listOf(2L, 5L)) { "Expected sorted order [2, 5], got ${sortedIdsSlot.captured}" }
    }

    @Test
    fun `잔액 부족 시 FAILED 상태로 저장`() = runTest {
        // given
        val idempotencyKey = "insufficient-balance-key"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("1000.00")

        val fromAccount = Account(
            id = fromAccountId,
            ownerName = "Alice",
            balance = BigDecimal("500.00"), // 잔액 부족
            status = AccountStatus.ACTIVE,
            version = 0L
        )
        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = null
        )
        val failedTransfer = pendingTransfer.fail("Insufficient balance")

        // Fast path: null
        // Double-check in main tx: null
        // Failure tx: null (PENDING rolled back)
        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        // Transaction execution: 1st = main tx (fails), 2nd = failure tx (succeeds)
        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            firstArg<suspend () -> Any>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen failedTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount, toAccount)
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when & then
        val exception = assertThrows<InsufficientBalanceException> {
            service.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)
        }

        // Verify 2 transactions executed (main + failure)
        assert(txCallCount == 2) { "Expected 2 transactions, got $txCallCount" }

        // Verify FAILED transfer was saved
        val transferSlot = mutableListOf<Transfer>()
        coVerify { transferRepository.save(capture(transferSlot)) }

        val failedTransferSaved = transferSlot.find { it.status == TransferStatus.FAILED }
        assert(failedTransferSaved != null) { "FAILED transfer should be saved" }
        assert(failedTransferSaved?.failureReason != null) { "Failure reason should be recorded" }

        // Verify audit event recorded
        coVerify(exactly = 1) { transferAuditRepository.save(any()) }
    }

    @Test
    fun `계좌 없음 시 FAILED 상태로 저장`() = runTest {
        // given
        val idempotencyKey = "account-not-found-key"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("100.00")

        val toAccount = Account(
            id = toAccountId,
            ownerName = "Bob",
            balance = BigDecimal("200.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        val pendingTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.PENDING,
            description = null
        )
        val failedTransfer = pendingTransfer.fail("From account not found: 1")

        // Fast path: null
        // Double-check in main tx: null
        // Failure tx: null (PENDING rolled back)
        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        // Transaction execution: 1st = main tx (fails), 2nd = failure tx (succeeds)
        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            firstArg<suspend () -> Any>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen failedTransfer
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(toAccount)
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when & then
        val exception = assertThrows<AccountNotFoundException> {
            service.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)
        }

        // Verify 2 transactions executed (main + failure)
        assert(txCallCount == 2) { "Expected 2 transactions, got $txCallCount" }

        // Verify FAILED transfer was saved
        val transferSlot = mutableListOf<Transfer>()
        coVerify { transferRepository.save(capture(transferSlot)) }

        val failedTransferSaved = transferSlot.find { it.status == TransferStatus.FAILED }
        assert(failedTransferSaved != null) { "FAILED transfer should be saved" }
        assert(failedTransferSaved?.failureReason != null) { "Failure reason should be recorded" }

        // Verify audit event recorded
        coVerify(exactly = 1) { transferAuditRepository.save(any()) }
    }

}
