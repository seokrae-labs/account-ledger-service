package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.application.port.`in`.GetTransfersUseCase
import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidTransferStatusTransitionException
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.TransferUseCase
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@WebFluxTest(TransferController::class)
@Import(GlobalExceptionHandler::class)
class TransferControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var transferUseCase: TransferUseCase

    @MockkBean
    private lateinit var getTransfersUseCase: GetTransfersUseCase

    @Test
    fun `이체 성공 - 201 Created`() = runTest {
        // given
        val idempotencyKey = "test-key-001"
        val fromAccountId = 1L
        val toAccountId = 2L
        val amount = BigDecimal("500.00")

        val transfer = Transfer(
            id = 1L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.COMPLETED,
            description = null
        )

        coEvery {
            transferUseCase.execute(idempotencyKey, fromAccountId, toAccountId, amount, null)
        } returns transfer

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": $fromAccountId,
                    "toAccountId": $toAccountId,
                    "amount": 500.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.amount").isEqualTo(500.00)
    }

    @Test
    fun `이체 성공 with description - 201 Created`() = runTest {
        // given
        val idempotencyKey = "test-key-002"
        val fromAccountId = 3L
        val toAccountId = 4L
        val amount = BigDecimal("150.00")
        val description = "Payment for services"

        val transfer = Transfer(
            id = 2L,
            idempotencyKey = idempotencyKey,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            status = TransferStatus.COMPLETED,
            description = description
        )

        coEvery {
            transferUseCase.execute(idempotencyKey, fromAccountId, toAccountId, amount, description)
        } returns transfer

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": $fromAccountId,
                    "toAccountId": $toAccountId,
                    "amount": 150.00,
                    "description": "$description"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(2)
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.amount").isEqualTo(150.00)
    }

    @Test
    fun `Idempotency-Key 헤더 누락 - 400 Bad Request`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_REQUEST")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `중복 이체 요청 - 409 Conflict`() = runTest {
        // given
        val idempotencyKey = "duplicate-key"
        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws DuplicateTransferException("Transfer already exists")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("DUPLICATE_TRANSFER")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `동시성 충돌 - 409 Conflict`() = runTest {
        // given
        val idempotencyKey = "concurrent-key"
        val optimisticLockException = com.labs.ledger.domain.exception.OptimisticLockException("Version mismatch")

        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws optimisticLockException

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("OPTIMISTIC_LOCK_FAILED")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `계좌 미존재 - 404 Not Found`() = runTest {
        // given
        val idempotencyKey = "no-account-key"
        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws AccountNotFoundException("Account not found")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 999,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("ACCOUNT_NOT_FOUND")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `서버 내부 오류 - 500 Internal Server Error`() = runTest {
        // given
        val idempotencyKey = "error-key"
        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws RuntimeException("Database connection failed")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.error").isEqualTo("INTERNAL_ERROR")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `null fromAccountId로 이체 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("fromAccountId")
    }

    @Test
    fun `null toAccountId로 이체 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("toAccountId")
    }

    @Test
    fun `0 금액으로 이체 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 0
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("amount")
    }

    @Test
    fun `잘못된 이체 상태 전이 - 409 Conflict`() = runTest {
        // given
        val idempotencyKey = "invalid-status-key"
        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws InvalidTransferStatusTransitionException("Cannot transition from COMPLETED to PENDING")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 100.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_TRANSFER_STATUS_TRANSITION")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `잔액 부족 시 - 400 Bad Request`() = runTest {
        // given
        val idempotencyKey = "insufficient-balance-key"
        coEvery {
            transferUseCase.execute(any(), any(), any(), any(), any())
        } throws InsufficientBalanceException("Insufficient balance. Required: 1000.00, Available: 500.00")

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": 1000.00
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INSUFFICIENT_BALANCE")
            .jsonPath("$.message").exists()
    }
}
