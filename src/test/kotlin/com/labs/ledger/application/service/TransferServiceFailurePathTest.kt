package com.labs.ledger.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.DeadLetterEntry
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * TransferService 비동기 실패영속화/DLQ fallback 단위 테스트
 *
 * 전략:
 * - asyncScope에 TestScope를 주입하여 advanceUntilIdle()로 비동기 코루틴 완료 제어
 * - transactionExecutor.execute를 coAnswers + 호출 카운트로 분기
 *   (1차: 비즈니스 로직, 2차: 비동기 영속화)
 */
class TransferServiceFailurePathTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val transferRepository: TransferRepository = mockk()
    private val transactionExecutor: TransactionExecutor = mockk()
    private val transferAuditRepository: TransferAuditRepository = mockk()
    private val failureRegistry: FailureRegistry = mockk(relaxed = true)
    private val deadLetterRepository: DeadLetterRepository = mockk(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var testScope: TestScope
    private lateinit var service: TransferService

    private val idempotencyKey = "test-failure-key"
    private val fromAccountId = 1L
    private val toAccountId = 2L
    private val amount = BigDecimal("500.00")

    // fromAccount: 잔액 10 < amount 500 → InsufficientBalanceException 유발
    private val fromAccount = Account(
        id = fromAccountId,
        ownerName = "Alice",
        balance = BigDecimal("10.00"),
        status = AccountStatus.ACTIVE
    )
    private val toAccount = Account(
        id = toAccountId,
        ownerName = "Bob",
        balance = BigDecimal("200.00"),
        status = AccountStatus.ACTIVE
    )
    private val pendingTransfer = Transfer(
        id = 1L,
        idempotencyKey = idempotencyKey,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
        status = TransferStatus.PENDING
    )

    @BeforeEach
    fun setUp() {
        testScope = TestScope()
        service = TransferService(
            accountRepository, ledgerEntryRepository, transferRepository,
            transactionExecutor, transferAuditRepository, failureRegistry,
            deadLetterRepository, objectMapper, testScope, meterRegistry
        )
        // 공통 스텁: fast-path 및 double-check 모두 null 반환
        every { failureRegistry.get(idempotencyKey) } returns null
        coEvery { transferRepository.findByIdempotencyKey(idempotencyKey) } returns null
        // pending 저장 스텁
        coEvery { transferRepository.save(match { it.status == TransferStatus.PENDING }) } returns pendingTransfer
        // 계좌 조회: 잔액 부족 계좌 반환 (fromAccountId=1, toAccountId=2 → sorted: [1, 2])
        coEvery { accountRepository.findByIdsForUpdate(listOf(1L, 2L)) } returns listOf(fromAccount, toAccount)
    }

    @Test
    fun `비동기 DB 저장 실패 시 DLQ에 fallback 저장한다`() = runTest {
        // given: 1차 tx → 비즈니스 로직 실행 (InsufficientBalanceException 발생)
        //        2차 tx → RuntimeException (DB 실패 시뮬레이션)
        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            when (txCallCount) {
                1 -> firstArg<suspend () -> Any>().invoke()
                else -> throw RuntimeException("DB persistence failed")
            }
        }
        coEvery { deadLetterRepository.save(any()) } returns mockk()

        // when
        assertThrows<InsufficientBalanceException> {
            service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))
        }

        // 비동기 코루틴 완료 대기
        testScope.advanceUntilIdle()

        // then: DLQ fallback 저장 확인
        coVerify(exactly = 1) { deadLetterRepository.save(any()) }
    }

    @Test
    fun `비동기 DB 저장 성공 후 failureRegistry에서 제거된다`() = runTest {
        // given
        val savedFailedTransfer = Transfer(
            id = 2L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.FAILED,
            failureReason = "Insufficient balance: Current: 10.00, Requested: 500.00"
        )
        coEvery { transferRepository.save(match { it.status == TransferStatus.FAILED }) } returns savedFailedTransfer
        coEvery { transferAuditRepository.save(any()) } returns mockk()

        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            when (txCallCount) {
                1 -> firstArg<suspend () -> Any>().invoke()  // 비즈니스 로직: InsufficientBalanceException
                else -> firstArg<suspend () -> Any>().invoke()  // 비동기 영속화: 성공
            }
        }

        // when
        assertThrows<InsufficientBalanceException> {
            service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))
        }
        testScope.advanceUntilIdle()

        // then: 성공 후 메모리에서 제거
        verify(exactly = 1) { failureRegistry.remove(idempotencyKey) }
    }

    @Test
    fun `DLQ payload에 필수 필드가 포함된다`() = runTest {
        // given
        var capturedEntry: DeadLetterEntry? = null
        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            when (txCallCount) {
                1 -> firstArg<suspend () -> Any>().invoke()
                else -> throw RuntimeException("DB error")
            }
        }
        coEvery { deadLetterRepository.save(any()) } answers {
            capturedEntry = firstArg()
            firstArg()
        }

        // when
        assertThrows<InsufficientBalanceException> {
            service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))
        }
        testScope.advanceUntilIdle()

        // then: payload 필수 필드 확인
        assert(capturedEntry != null) { "DLQ entry must be saved" }
        @Suppress("UNCHECKED_CAST")
        val parsedPayload = objectMapper.readValue(capturedEntry!!.payload, Map::class.java) as Map<String, Any?>
        assert(parsedPayload.containsKey("fromAccountId")) { "fromAccountId 필드 누락" }
        assert(parsedPayload.containsKey("toAccountId")) { "toAccountId 필드 누락" }
        assert(parsedPayload.containsKey("amount")) { "amount 필드 누락" }
        assert(parsedPayload.containsKey("idempotencyKey")) { "idempotencyKey 필드 누락" }
        assert(parsedPayload.containsKey("errorMessage")) { "errorMessage 필드 누락" }
        assert(parsedPayload.containsKey("originalException")) { "originalException 필드 누락" }
        assert(parsedPayload["idempotencyKey"] == idempotencyKey)
        assert(parsedPayload["fromAccountId"].toString() == fromAccountId.toString())
        assert(parsedPayload["toAccountId"].toString() == toAccountId.toString())
        assert(parsedPayload["originalException"] == "InsufficientBalanceException")
    }

    @Test
    fun `동일 idempotencyKey 재요청 시 메모리 캐시에서 FAILED 이체를 반환한다`() = runTest {
        // given: 메모리 캐시에 FAILED 이체 등록
        val failedTransfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.FAILED,
            failureReason = "Insufficient balance"
        )
        every { failureRegistry.get(idempotencyKey) } returns FailureRecord(
            transfer = failedTransfer,
            errorMessage = "Insufficient balance"
        )

        // when: 동일 key로 재요청
        val result = service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))

        // then: DB 조회 없이 즉시 반환
        assert(result == failedTransfer)
        coVerify(exactly = 0) { transferRepository.findByIdempotencyKey(any()) }
        coVerify(exactly = 0) { transactionExecutor.execute<Any>(any()) }
    }

    @Test
    fun `DLQ 저장도 실패하면 failureRegistry에 레코드가 유지된다`() = runTest {
        // given: DB 저장 실패 + DLQ 저장도 실패
        var txCallCount = 0
        coEvery { transactionExecutor.execute<Any>(any()) } coAnswers {
            txCallCount++
            when (txCallCount) {
                1 -> firstArg<suspend () -> Any>().invoke()
                else -> throw RuntimeException("DB persistence failed")
            }
        }
        coEvery { deadLetterRepository.save(any()) } throws RuntimeException("DLQ also failed")

        // when
        assertThrows<InsufficientBalanceException> {
            service.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))
        }
        testScope.advanceUntilIdle()

        // then: failureRegistry.remove 호출 안 됨 (레코드 유지)
        verify(exactly = 0) { failureRegistry.remove(idempotencyKey) }
    }
}
