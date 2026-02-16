package com.labs.ledger.application.service

import com.labs.ledger.adapter.out.persistence.repository.TransferAuditEventEntityRepository
import com.labs.ledger.adapter.out.persistence.repository.TransferEntityRepository
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.TransferAuditEventType
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.AccountRepository
import com.labs.ledger.domain.port.TransferUseCase
import com.labs.ledger.support.AbstractIntegrationTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

/**
 * Transfer Failure Durability Integration Test
 *
 * Verifies:
 * 1. FAILED status persists after main transaction rollback
 * 2. Audit events persist independently
 * 3. Idempotency behavior with FAILED transfers
 * 4. Successful transfers record audit events
 */
class TransferFailureDurabilityTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var transferUseCase: TransferUseCase

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transferEntityRepository: TransferEntityRepository

    @Autowired
    private lateinit var transferAuditRepository: TransferAuditEventEntityRepository

    @Test
    fun `비즈니스 실패 후 FAILED row 유지`() = runBlocking {
        // given: 잔액이 부족한 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Alice",
                balance = BigDecimal("500.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Bob",
                balance = BigDecimal("200.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "insufficient-balance-test"
        val excessiveAmount = BigDecimal("1000.00") // > 500

        // when: 이체 시도 (실패 예상)
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = excessiveAmount,
                description = "Test excessive transfer"
            )
        }

        // Wait for async persistence (최대 2초)
        var savedTransfer: com.labs.ledger.adapter.out.persistence.entity.TransferEntity? = null
        repeat(20) {
            delay(100)
            savedTransfer = transferEntityRepository.findByIdempotencyKey(idempotencyKey)
            if (savedTransfer != null) return@repeat
        }

        // then: DB에 FAILED 상태로 저장되어 있어야 함
        assert(savedTransfer != null) {
            "Transfer with FAILED status should persist in DB"
        }
        val transfer = savedTransfer!!
        assert(transfer.status == TransferStatus.FAILED.name) {
            "Expected FAILED status, got ${transfer.status}"
        }
        assert(transfer.failureReason != null) {
            "Failure reason should be recorded"
        }
        assert(transfer.failureReason!!.contains("Insufficient balance")) {
            "Expected 'Insufficient balance' in reason, got: ${transfer.failureReason}"
        }
    }

    @Test
    fun `비즈니스 실패 후 audit 이벤트 유지`() = runBlocking {
        // given
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Charlie",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Dave",
                balance = BigDecimal("50.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "audit-event-test"
        val excessiveAmount = BigDecimal("500.00")

        // when: 실패하는 이체 시도
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = excessiveAmount,
                description = null
            )
        }

        // Wait for async persistence (최대 2초)
        var auditEvent: com.labs.ledger.adapter.out.persistence.entity.TransferAuditEventEntity? = null
        repeat(20) {
            delay(100)
            auditEvent = transferAuditRepository.findByIdempotencyKey(idempotencyKey)
            if (auditEvent != null) return@repeat
        }

        // then: audit 이벤트 확인
        assert(auditEvent != null) {
            "Audit event should be persisted"
        }
        val event = auditEvent!!
        assert(event.eventType == TransferAuditEventType.TRANSFER_FAILED_BUSINESS.name) {
            "Expected TRANSFER_FAILED_BUSINESS, got ${event.eventType}"
        }
        assert(event.transferStatus == TransferStatus.FAILED.name) {
            "Audit event should record FAILED status"
        }
        assert(event.reasonCode != null) {
            "Reason code should be recorded"
        }
    }

    @Test
    fun `동일 idempotency key 재요청 시 FAILED 반환`() = runBlocking {
        // given: 실패한 이체 기록
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Eve",
                balance = BigDecimal("50.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Frank",
                balance = BigDecimal("100.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "idempotency-failed-test"
        val excessiveAmount = BigDecimal("200.00")

        // First attempt - fails
        assertThrows<InsufficientBalanceException> {
            transferUseCase.execute(
                idempotencyKey = idempotencyKey,
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = excessiveAmount,
                description = null
            )
        }

        // when: 동일한 idempotency key로 재요청
        val result = transferUseCase.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccount.id!!,
            toAccountId = toAccount.id!!,
            amount = excessiveAmount,
            description = null
        )

        // then: 기존 FAILED 상태 반환 (예외 발생 X)
        assert(result.status == TransferStatus.FAILED) {
            "Expected FAILED status on retry, got ${result.status}"
        }
        assert(result.idempotencyKey == idempotencyKey) {
            "Idempotency key should match"
        }
    }

    @Test
    fun `성공 이체 시 COMPLETED 감사 이벤트 기록`() = runBlocking {
        // given: 정상적인 계좌
        val fromAccount = accountRepository.save(
            Account(
                ownerName = "Grace",
                balance = BigDecimal("1000.00"),
                status = AccountStatus.ACTIVE
            )
        )
        val toAccount = accountRepository.save(
            Account(
                ownerName = "Heidi",
                balance = BigDecimal("500.00"),
                status = AccountStatus.ACTIVE
            )
        )

        val idempotencyKey = "success-audit-test"
        val amount = BigDecimal("300.00")

        // when: 정상 이체
        val result = transferUseCase.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccount.id!!,
            toAccountId = toAccount.id!!,
            amount = amount,
            description = "Success test"
        )

        // then: 성공 상태 확인
        assert(result.status == TransferStatus.COMPLETED) {
            "Transfer should be completed"
        }

        // and: audit 이벤트 확인
        val auditEvent = transferAuditRepository.findByIdempotencyKey(idempotencyKey)
        assert(auditEvent != null) {
            "Audit event should be recorded for successful transfer"
        }
        assert(auditEvent!!.eventType == TransferAuditEventType.TRANSFER_COMPLETED.name) {
            "Expected TRANSFER_COMPLETED, got ${auditEvent.eventType}"
        }
        assert(auditEvent.transferStatus == TransferStatus.COMPLETED.name) {
            "Audit event should record COMPLETED status"
        }
        assert(auditEvent.transferId == result.id) {
            "Audit event should reference the transfer ID"
        }
    }
}
