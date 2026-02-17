package com.labs.ledger.adapter.`in`.web

import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import com.labs.ledger.RestDocsConfiguration
import com.labs.ledger.support.TestSecurityConfig
import com.labs.ledger.domain.port.GetTransfersUseCase
import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.DuplicateTransferException
import com.labs.ledger.domain.exception.InsufficientBalanceException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidTransferStatusTransitionException
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferCommand
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.TransferUseCase
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@WebFluxTest(
    controllers = [TransferController::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ["com\\.labs\\.ledger\\.infrastructure\\.security\\..*"]
    )]
)
@AutoConfigureRestDocs
@Import(GlobalExceptionHandler::class, RestDocsConfiguration::class, TestSecurityConfig::class)
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
            transferUseCase.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount))
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
            .consumeWith(
                document(
                    "transfer-create",
                    requestHeaders(
                        headerWithName("Idempotency-Key").description("멱등성 키 (UUID 권장, 중복 이체 방지)")
                    ),
                    relaxedRequestFields(
                        fieldWithPath("fromAccountId").description("출금 계좌 ID"),
                        fieldWithPath("toAccountId").description("입금 계좌 ID"),
                        fieldWithPath("amount").description("이체 금액")
                    ),
                    relaxedResponseFields(
                        fieldWithPath("id").description("이체 ID"),
                        fieldWithPath("idempotencyKey").description("멱등성 키"),
                        fieldWithPath("fromAccountId").description("출금 계좌 ID"),
                        fieldWithPath("toAccountId").description("입금 계좌 ID"),
                        fieldWithPath("amount").description("이체 금액"),
                        fieldWithPath("status").description("이체 상태 (PENDING, COMPLETED, FAILED)"),
                        fieldWithPath("description").description("이체 설명"),
                        fieldWithPath("failureReason").description("실패 사유 (실패 시에만 존재)").optional(),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")
                    )
                )
            )
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
            transferUseCase.execute(TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount, description))
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
    fun `Idempotency-Key 256자 초과 - 400 Bad Request`() = runTest {
        // given - 256자 키
        val tooLongKey = "a".repeat(256)

        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", tooLongKey)
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
            transferUseCase.execute(any<TransferCommand>())
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
            transferUseCase.execute(any<TransferCommand>())
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
            transferUseCase.execute(any<TransferCommand>())
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
            transferUseCase.execute(any<TransferCommand>())
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
            transferUseCase.execute(any<TransferCommand>())
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
            transferUseCase.execute(any<TransferCommand>())
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

    @Test
    fun `자기 자신에게 이체 시도 - 400 Invalid Request`() = runTest {
        // when & then - TransferCommand.init require(fromAccountId != toAccountId) → IllegalArgumentException → INVALID_REQUEST
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", "self-transfer-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 1,
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
    fun `음수 금액으로 이체 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/transfers")
            .header("Idempotency-Key", "negative-amount-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "fromAccountId": 1,
                    "toAccountId": 2,
                    "amount": -100.00
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
    fun `비활성 출금 계좌로 이체 시도 - 400 Bad Request`() = runTest {
        // given
        val idempotencyKey = "inactive-from-key"
        coEvery {
            transferUseCase.execute(any<TransferCommand>())
        } throws InvalidAccountStatusException("Source account is not active")

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
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_ACCOUNT_STATUS")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `비활성 입금 계좌로 이체 시도 - 400 Bad Request`() = runTest {
        // given
        val idempotencyKey = "inactive-to-key"
        coEvery {
            transferUseCase.execute(any<TransferCommand>())
        } throws InvalidAccountStatusException("Destination account is not active")

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
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_ACCOUNT_STATUS")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `이체 목록 조회 시 page가 음수이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/transfers?page=-1&size=20")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("page")
    }

    @Test
    fun `이체 목록 조회 시 size가 0이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/transfers?page=0&size=0")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("size")
    }

    @Test
    fun `이체 목록 조회 시 size가 100 초과이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/transfers?page=0&size=101")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("size")
    }

    @Test
    fun `이체 목록 조회 성공 - 200 OK`() = runTest {
        // given
        val transfers = listOf(
            Transfer(
                id = 1L,
                idempotencyKey = "key-001",
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = BigDecimal("500"),
                status = TransferStatus.COMPLETED,
                description = "Payment"
            ),
            Transfer(
                id = 2L,
                idempotencyKey = "key-002",
                fromAccountId = 2L,
                toAccountId = 3L,
                amount = BigDecimal("300"),
                status = TransferStatus.COMPLETED,
                description = "Refund"
            )
        )
        val page = com.labs.ledger.domain.port.TransfersPage(
            transfers = transfers,
            page = 0,
            size = 20,
            totalElements = 2
        )

        coEvery { getTransfersUseCase.execute(0, 20) } returns page

        // when & then
        webTestClient.get()
            .uri("/api/transfers?page=0&size=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .consumeWith(
                document(
                    "transfers-list",
                    queryParameters(
                        parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                        parameterWithName("size").description("페이지 크기 (1-100)").optional()
                    ),
                    relaxedResponseFields(
                        fieldWithPath("content[]").description("이체 목록"),
                        fieldWithPath("content[].id").description("이체 ID"),
                        fieldWithPath("content[].idempotencyKey").description("멱등성 키"),
                        fieldWithPath("content[].fromAccountId").description("출금 계좌 ID"),
                        fieldWithPath("content[].toAccountId").description("입금 계좌 ID"),
                        fieldWithPath("content[].amount").description("이체 금액"),
                        fieldWithPath("content[].status").description("이체 상태"),
                        fieldWithPath("content[].description").description("이체 설명"),
                        fieldWithPath("page").description("현재 페이지"),
                        fieldWithPath("size").description("페이지 크기"),
                        fieldWithPath("totalElements").description("전체 요소 수"),
                        fieldWithPath("totalPages").description("전체 페이지 수"),
                        fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                        fieldWithPath("hasPrevious").description("이전 페이지 존재 여부")
                    )
                )
            )
    }
}
