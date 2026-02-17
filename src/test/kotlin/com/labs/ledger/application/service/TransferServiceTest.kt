package com.labs.ledger.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferCommand
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.DeadLetterRepository
import com.labs.ledger.domain.port.FailureRecord
import com.labs.ledger.domain.port.FailureRegistry
import com.labs.ledger.domain.port.LedgerEntryRepository
import com.labs.ledger.domain.port.TransactionExecutor
import com.labs.ledger.domain.port.TransferAuditRepository
import com.labs.ledger.domain.port.TransferRepository
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

/**
 * TransferService 핵심 시나리오 단위 테스트
 *
 * 목적: 통합 테스트로 재현하기 어려운 특수 시나리오 검증
 * - Race Condition (멱등성 double-check)
 * - Deadlock Prevention (계좌 ID 정렬)
 * - Fast Path 최적화 (트랜잭션 미진입)
 *
 * 기본 플로우 및 예외 처리는 통합 테스트로 검증
 */
class TransferServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val transferRepository: TransferRepository = mockk()
    private val transactionExecutor: TransactionExecutor = mockk()
    private val transferAuditRepository: TransferAuditRepository = mockk()
    private val failureRegistry: FailureRegistry = mockk(relaxed = true)
    private val deadLetterRepository: DeadLetterRepository = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val asyncScope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val service = TransferService(
        accountRepository,
        ledgerEntryRepository,
        transferRepository,
        transactionExecutor,
        transferAuditRepository,
        failureRegistry,
        deadLetterRepository,
        objectMapper,
        asyncScope
    )

    // ============================================
    // Fast Path 최적화 검증 (3개)
    // ============================================

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

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null
        // DB: hit
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns completedTransfer

        // when
        val result = service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))

        // then
        assert(result == completedTransfer)

        // Fast path에서 반환 → 트랜잭션 미진입 (성능 최적화)
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

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null
        // DB: PENDING
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns pendingTransfer

        // when & then
        assertThrows<DuplicateTransferException> {
            service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))
        }

        // Fast path에서 예외 발생 → 트랜잭션 미진입
        coVerify(exactly = 0) { transactionExecutor.execute<Transfer>(any()) }
    }

    @Test
    fun `멱등성 fast path - FAILED 반환 (메모리 캐시)`() = runTest {
        // given
        val idempotencyKey = "failed-key"
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            failureReason = "Insufficient balance"
        )

        // Memory cache: hit (매우 빠른 경로)
        every { failureRegistry.get(idempotencyKey) } returns FailureRecord(
            transfer = failedTransfer,
            errorMessage = "Insufficient balance"
        )

        // when
        val result = service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))

        // then
        assert(result == failedTransfer)

        // 메모리 캐시에서 즉시 반환 → DB 조회도 안 함
        coVerify(exactly = 0) { transferRepository.findByIdempotencyKey(any()) }
        coVerify(exactly = 0) { transactionExecutor.execute<Transfer>(any()) }
    }

    // ============================================
    // Race Condition 검증 (3개)
    // ============================================

    @Test
    fun `멱등성 double-check - COMPLETED (race condition)`() = runTest {
        // given: 동시 요청 시나리오
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

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null

        // Fast path: null (첫 번째 호출)
        // Double-check: COMPLETED (두 번째 호출 - 다른 요청이 완료함)
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            if (findCallCount == 1) null else completedTransfer
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        // when
        val result = service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))

        // then
        assert(result == completedTransfer) { "Should return existing COMPLETED transfer" }

        // Double-check가 COMPLETED를 발견 → 기존 Transfer 반환
        coVerify(exactly = 0) { transferRepository.save(any()) }
        coVerify(exactly = 0) { accountRepository.findByIdsForUpdate(any()) }
    }

    @Test
    fun `멱등성 double-check - PENDING (race condition)`() = runTest {
        // given: 동시 요청 시나리오
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

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null

        // Fast path: null (첫 번째 호출)
        // Double-check: PENDING (두 번째 호출 - 다른 요청이 생성 중)
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
            service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))
        }

        // Double-check가 PENDING 발견 → DuplicateTransferException
        coVerify(exactly = 0) { transferRepository.save(any()) }
        coVerify(exactly = 0) { accountRepository.findByIdsForUpdate(any()) }
    }

    @Test
    fun `멱등성 double-check - FAILED (race condition)`() = runTest {
        // given: 동시 요청 시나리오
        val idempotencyKey = "race-key-failed"
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            status = TransferStatus.FAILED,
            failureReason = "Insufficient balance"
        )

        var findCallCount = 0

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null

        // Fast path: null (첫 번째 호출)
        // Double-check: FAILED (두 번째 호출 - 다른 요청이 실패함)
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            if (findCallCount == 1) null else failedTransfer
        }

        // Transaction execution
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        // when
        val result = service.execute(TransferCommand(idempotencyKey, 1L, 2L, BigDecimal("100.00")))

        // then
        assert(result == failedTransfer) { "Should return existing FAILED transfer" }

        // Double-check가 FAILED 발견 → 기존 Transfer 반환
        coVerify(exactly = 0) { transferRepository.save(any()) }
        coVerify(exactly = 0) { accountRepository.findByIdsForUpdate(any()) }
    }

    // ============================================
    // Deadlock Prevention 검증 (1개)
    // ============================================

    @Test
    fun `Deadlock prevention - 계좌 ID 정렬 검증`() = runTest {
        // given: fromAccountId > toAccountId
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

        // Memory cache: miss
        every { failureRegistry.get(idempotencyKey) } returns null

        // DB checks
        var findCallCount = 0
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } answers {
            findCallCount++
            null
        }

        // Transaction
        coEvery { transactionExecutor.execute<Transfer>(any()) } coAnswers {
            firstArg<suspend () -> Transfer>().invoke()
        }

        coEvery { transferRepository.save(any()) } returns pendingTransfer andThen completedTransfer

        // 정렬된 순서로 호출되어야 함: [2, 5]
        coEvery { accountRepository.findByIdsForUpdate(listOf(2L, 5L)) } returns listOf(toAccount, fromAccount)

        // Account save (debit, credit)
        coEvery { accountRepository.save(any()) } answers { firstArg() }

        coEvery { ledgerEntryRepository.saveAll(any()) } returns emptyList()
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        // when
        service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))

        // then: ID가 정렬되어 조회되어야 함 (Deadlock 방지)
        coVerify(exactly = 1) {
            accountRepository.findByIdsForUpdate(
                match { ids ->
                    ids.size == 2 && ids[0] == 2L && ids[1] == 5L
                }
            )
        }
    }
}
