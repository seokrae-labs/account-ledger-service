package com.labs.ledger.adapter.`in`.web

import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import com.labs.ledger.RestDocsConfiguration
import com.labs.ledger.support.TestSecurityConfig
import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidAmountException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.port.GetAccountsUseCase
import com.labs.ledger.domain.port.GetLedgerEntriesUseCase
import com.labs.ledger.domain.port.CreateAccountUseCase
import com.labs.ledger.domain.port.DepositUseCase
import com.labs.ledger.domain.port.GetAccountBalanceUseCase
import com.labs.ledger.domain.port.UpdateAccountStatusUseCase
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
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@WebFluxTest(
    controllers = [AccountController::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ["com\\.labs\\.ledger\\.infrastructure\\.security\\..*"]
    )]
)
@AutoConfigureRestDocs
@Import(GlobalExceptionHandler::class, RestDocsConfiguration::class, TestSecurityConfig::class)
class AccountControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var createAccountUseCase: CreateAccountUseCase

    @MockkBean
    private lateinit var depositUseCase: DepositUseCase

    @MockkBean
    private lateinit var getAccountBalanceUseCase: GetAccountBalanceUseCase

    @MockkBean
    private lateinit var getAccountsUseCase: GetAccountsUseCase

    @MockkBean
    private lateinit var getLedgerEntriesUseCase: GetLedgerEntriesUseCase

    @MockkBean
    private lateinit var updateAccountStatusUseCase: UpdateAccountStatusUseCase

    @Test
    fun `계좌 생성 성공 - 201 Created`() = runTest {
        // given
        val ownerName = "John Doe"
        val account = Account(
            id = 1L,
            ownerName = ownerName,
            balance = BigDecimal.ZERO,
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        coEvery { createAccountUseCase.execute(ownerName) } returns account

        // when & then
        webTestClient.post()
            .uri("/api/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"ownerName":"$ownerName"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.ownerName").isEqualTo(ownerName)
            .jsonPath("$.balance").isEqualTo(0)
            .consumeWith(
                document(
                    "account-create",
                    relaxedRequestFields(
                        fieldWithPath("ownerName").description("계좌 소유자 이름")
                    ),
                    relaxedResponseFields(
                        fieldWithPath("id").description("계좌 ID"),
                        fieldWithPath("ownerName").description("계좌 소유자 이름"),
                        fieldWithPath("balance").description("계좌 잔액"),
                        fieldWithPath("status").description("계좌 상태 (ACTIVE, SUSPENDED, CLOSED)"),
                        fieldWithPath("version").description("버전 (Optimistic Lock)"),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")
                    )
                )
            )
    }

    @Test
    fun `입금 성공 - 200 OK`() = runTest {
        // given
        val accountId = 1L
        val amount = BigDecimal("500.00")
        val account = Account(
            id = accountId,
            ownerName = "John Doe",
            balance = amount,
            status = AccountStatus.ACTIVE,
            version = 1L
        )

        coEvery { depositUseCase.execute(accountId, amount, null) } returns account

        // when & then
        webTestClient.post()
            .uri("/api/accounts/$accountId/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":500.00}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(accountId)
            .jsonPath("$.balance").isEqualTo(500.00)
            .consumeWith(
                document(
                    "account-deposit",
                    relaxedRequestFields(
                        fieldWithPath("amount").description("입금 금액 (양수)")
                    ),
                    relaxedResponseFields(
                        fieldWithPath("id").description("계좌 ID"),
                        fieldWithPath("ownerName").description("계좌 소유자 이름"),
                        fieldWithPath("balance").description("계좌 잔액 (입금 후)"),
                        fieldWithPath("status").description("계좌 상태"),
                        fieldWithPath("version").description("버전 (Optimistic Lock)"),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")
                    )
                )
            )
    }

    @Test
    fun `입금 성공 with description - 200 OK`() = runTest {
        // given
        val accountId = 1L
        val amount = BigDecimal("200.00")
        val description = "Bonus payment"
        val account = Account(
            id = accountId,
            ownerName = "John Doe",
            balance = amount,
            status = AccountStatus.ACTIVE,
            version = 1L
        )

        coEvery { depositUseCase.execute(accountId, amount, description) } returns account

        // when & then
        webTestClient.post()
            .uri("/api/accounts/$accountId/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":200.00,"description":"$description"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(accountId)
            .jsonPath("$.balance").isEqualTo(200.00)
    }

    @Test
    fun `계좌 조회 성공 - 200 OK`() = runTest {
        // given
        val accountId = 1L
        val account = Account(
            id = accountId,
            ownerName = "John Doe",
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            version = 0L
        )

        coEvery { getAccountBalanceUseCase.execute(accountId) } returns account

        // when & then
        webTestClient.get()
            .uri("/api/accounts/$accountId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(accountId)
            .jsonPath("$.balance").isEqualTo(1000.00)
            .consumeWith(
                document(
                    "account-get",
                    relaxedResponseFields(
                        fieldWithPath("id").description("계좌 ID"),
                        fieldWithPath("ownerName").description("계좌 소유자 이름"),
                        fieldWithPath("balance").description("계좌 잔액"),
                        fieldWithPath("status").description("계좌 상태"),
                        fieldWithPath("version").description("버전 (Optimistic Lock)"),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")
                    )
                )
            )
    }

    @Test
    fun `계좌 미존재 - 404 Not Found`() = runTest {
        // given
        val accountId = 999L
        coEvery { getAccountBalanceUseCase.execute(accountId) } throws AccountNotFoundException("Account not found")

        // when & then
        webTestClient.get()
            .uri("/api/accounts/$accountId")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("ACCOUNT_NOT_FOUND")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `비활성 계좌 입금 시도 - 400 Bad Request`() = runTest {
        // given
        val accountId = 1L
        val amount = BigDecimal("100.00")
        coEvery {
            depositUseCase.execute(accountId, amount, null)
        } throws InvalidAccountStatusException("Account is not active")

        // when & then
        webTestClient.post()
            .uri("/api/accounts/$accountId/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":100.00}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_ACCOUNT_STATUS")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `음수 금액 입금 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/accounts/1/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":-100.00}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("amount")
    }

    @Test
    fun `빈 ownerName으로 계좌 생성 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"ownerName":""}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("ownerName")
            .jsonPath("$.errors[0].message").exists()
    }

    @Test
    fun `0 금액으로 입금 시도 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.post()
            .uri("/api/accounts/1/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":0}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("amount")
            .jsonPath("$.errors[0].message").exists()
    }

    @Test
    fun `계좌 목록 조회 성공 - 200 OK`() = runTest {
        // given
        val accounts = listOf(
            Account(id = 1L, ownerName = "Alice", balance = BigDecimal("1000"), status = AccountStatus.ACTIVE, version = 0L),
            Account(id = 2L, ownerName = "Bob", balance = BigDecimal("2000"), status = AccountStatus.ACTIVE, version = 0L)
        )
        val page = com.labs.ledger.domain.port.AccountsPage(
            accounts = accounts,
            page = 0,
            size = 20,
            totalElements = 2
        )

        coEvery { getAccountsUseCase.execute(0, 20) } returns page

        // when & then
        webTestClient.get()
            .uri("/api/accounts?page=0&size=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .consumeWith(
                document(
                    "accounts-list",
                    queryParameters(
                        parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                        parameterWithName("size").description("페이지 크기 (1-100)").optional()
                    ),
                    relaxedResponseFields(
                        fieldWithPath("content[]").description("계좌 목록"),
                        fieldWithPath("content[].id").description("계좌 ID"),
                        fieldWithPath("content[].ownerName").description("소유자 이름"),
                        fieldWithPath("content[].balance").description("잔액"),
                        fieldWithPath("content[].status").description("상태"),
                        fieldWithPath("page").description("현재 페이지 번호"),
                        fieldWithPath("size").description("페이지 크기"),
                        fieldWithPath("totalElements").description("전체 요소 수"),
                        fieldWithPath("totalPages").description("전체 페이지 수"),
                        fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                        fieldWithPath("hasPrevious").description("이전 페이지 존재 여부")
                    )
                )
            )
    }

    @Test
    fun `원장 엔트리 조회 성공 - 200 OK`() = runTest {
        // given
        val accountId = 1L
        val entries = listOf(
            LedgerEntry(
                id = 1L,
                accountId = accountId,
                type = LedgerEntryType.CREDIT,
                amount = BigDecimal("500"),
                referenceId = null,
                description = "Initial deposit"
            )
        )
        val page = com.labs.ledger.domain.port.LedgerEntriesPage(
            entries = entries,
            page = 0,
            size = 20,
            totalElements = 1
        )

        coEvery { getLedgerEntriesUseCase.execute(accountId, 0, 20) } returns page

        // when & then
        webTestClient.get()
            .uri("/api/accounts/$accountId/ledger-entries?page=0&size=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .consumeWith(
                document(
                    "account-ledger-entries",
                    queryParameters(
                        parameterWithName("page").description("페이지 번호").optional(),
                        parameterWithName("size").description("페이지 크기").optional()
                    ),
                    relaxedResponseFields(
                        fieldWithPath("content[]").description("원장 엔트리 목록"),
                        fieldWithPath("content[].id").description("엔트리 ID"),
                        fieldWithPath("content[].accountId").description("계좌 ID"),
                        fieldWithPath("content[].type").description("거래 유형 (DEPOSIT, WITHDRAWAL)"),
                        fieldWithPath("content[].amount").description("거래 금액"),
                        fieldWithPath("content[].referenceId").description("참조 ID (이체 ID 등)"),
                        fieldWithPath("content[].description").description("거래 설명"),
                        fieldWithPath("page").description("현재 페이지"),
                        fieldWithPath("size").description("페이지 크기"),
                        fieldWithPath("totalElements").description("전체 요소 수")
                    )
                )
            )
    }

    @Test
    fun `입금 시 계좌 미존재 - 404 Not Found`() = runTest {
        // given
        val accountId = 999L
        val amount = BigDecimal("100.00")
        coEvery { depositUseCase.execute(accountId, amount, null) } throws AccountNotFoundException("Account not found")

        // when & then
        webTestClient.post()
            .uri("/api/accounts/$accountId/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":100.00}""")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("ACCOUNT_NOT_FOUND")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `계좌 목록 조회 시 page가 음수이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/accounts?page=-1&size=20")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("page")
    }

    @Test
    fun `계좌 목록 조회 시 size가 0이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/accounts?page=0&size=0")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("size")
    }

    @Test
    fun `계좌 목록 조회 시 size가 100 초과이면 - 400 Validation Failed`() = runTest {
        // when & then
        webTestClient.get()
            .uri("/api/accounts?page=0&size=101")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Request validation failed")
            .jsonPath("$.errors[0].field").isEqualTo("size")
    }

    @Test
    fun `원장 엔트리 조회 시 계좌 미존재 - 404 Not Found`() = runTest {
        // given
        val accountId = 999L
        coEvery { getLedgerEntriesUseCase.execute(accountId, 0, 20) } throws AccountNotFoundException("Account not found")

        // when & then
        webTestClient.get()
            .uri("/api/accounts/$accountId/ledger-entries?page=0&size=20")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("ACCOUNT_NOT_FOUND")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `잘못된 상태 값으로 상태 변경 시도 - 400 Invalid Request`() = runTest {
        // when & then - AccountStatus.valueOf fails → IllegalArgumentException → INVALID_REQUEST
        webTestClient.patch()
            .uri("/api/accounts/1/status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"INVALID_STATUS"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_REQUEST")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `상태 변경 시 계좌 미존재 - 404 Not Found`() = runTest {
        // given
        val accountId = 999L
        coEvery { updateAccountStatusUseCase.execute(accountId, AccountStatus.SUSPENDED) } throws AccountNotFoundException("Account not found")

        // when & then
        webTestClient.patch()
            .uri("/api/accounts/$accountId/status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"SUSPENDED"}""")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("ACCOUNT_NOT_FOUND")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `이미 활성 상태인 계좌에 ACTIVE 변경 시도 - 400 Bad Request`() = runTest {
        // given
        val accountId = 1L
        coEvery {
            updateAccountStatusUseCase.execute(accountId, AccountStatus.ACTIVE)
        } throws InvalidAccountStatusException("Account is already active")

        // when & then
        webTestClient.patch()
            .uri("/api/accounts/$accountId/status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"ACTIVE"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_ACCOUNT_STATUS")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `닫힌 계좌에 ACTIVE 변경 시도 - 400 Bad Request`() = runTest {
        // given
        val accountId = 1L
        coEvery {
            updateAccountStatusUseCase.execute(accountId, AccountStatus.ACTIVE)
        } throws InvalidAccountStatusException("Cannot activate a closed account")

        // when & then
        webTestClient.patch()
            .uri("/api/accounts/$accountId/status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"ACTIVE"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_ACCOUNT_STATUS")
            .jsonPath("$.message").exists()
    }

    @Test
    fun `계좌 상태 변경 성공 - 200 OK`() = runTest {
        // given
        val accountId = 1L
        val updatedAccount = Account(
            id = accountId,
            ownerName = "John Doe",
            balance = BigDecimal("1000"),
            status = AccountStatus.SUSPENDED,
            version = 1L
        )

        coEvery { updateAccountStatusUseCase.execute(accountId, AccountStatus.SUSPENDED) } returns updatedAccount

        // when & then
        webTestClient.patch()
            .uri("/api/accounts/$accountId/status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status":"SUSPENDED"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("SUSPENDED")
            .consumeWith(
                document(
                    "account-status-update",
                    relaxedRequestFields(
                        fieldWithPath("status").description("변경할 상태 (ACTIVE, SUSPENDED, CLOSED)")
                    ),
                    relaxedResponseFields(
                        fieldWithPath("id").description("계좌 ID"),
                        fieldWithPath("ownerName").description("소유자 이름"),
                        fieldWithPath("balance").description("잔액"),
                        fieldWithPath("status").description("변경된 상태"),
                        fieldWithPath("version").description("버전 (Optimistic Lock)"),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")
                    )
                )
            )
    }
}
