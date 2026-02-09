package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.domain.exception.AccountNotFoundException
import com.labs.ledger.domain.exception.InvalidAccountStatusException
import com.labs.ledger.domain.exception.InvalidAmountException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.CreateAccountUseCase
import com.labs.ledger.domain.port.DepositUseCase
import com.labs.ledger.domain.port.GetAccountBalanceUseCase
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

@WebFluxTest(AccountController::class)
@Import(GlobalExceptionHandler::class)
class AccountControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var createAccountUseCase: CreateAccountUseCase

    @MockkBean
    private lateinit var depositUseCase: DepositUseCase

    @MockkBean
    private lateinit var getAccountBalanceUseCase: GetAccountBalanceUseCase

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
    fun `음수 금액 입금 시도 - 400 Bad Request`() = runTest {
        // given
        val accountId = 1L
        val amount = BigDecimal("-100.00")
        coEvery {
            depositUseCase.execute(accountId, amount, null)
        } throws InvalidAmountException("Amount must be positive")

        // when & then
        webTestClient.post()
            .uri("/api/accounts/$accountId/deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount":-100.00}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_AMOUNT")
            .jsonPath("$.message").exists()
    }
}
