package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.AccountResponse
import com.labs.ledger.adapter.`in`.web.dto.CreateAccountRequest
import com.labs.ledger.adapter.`in`.web.dto.DepositRequest
import com.labs.ledger.domain.port.CreateAccountUseCase
import com.labs.ledger.domain.port.DepositUseCase
import com.labs.ledger.domain.port.GetAccountBalanceUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val createAccountUseCase: CreateAccountUseCase,
    private val depositUseCase: DepositUseCase,
    private val getAccountBalanceUseCase: GetAccountBalanceUseCase
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createAccount(@Valid @RequestBody request: CreateAccountRequest): AccountResponse {
        val account = createAccountUseCase.execute(request.ownerName)
        return AccountResponse.from(account)
    }

    @PostMapping("/{id}/deposits")
    suspend fun deposit(
        @PathVariable id: Long,
        @Valid @RequestBody request: DepositRequest
    ): AccountResponse {
        val account = depositUseCase.execute(id, request.amount, request.description)
        return AccountResponse.from(account)
    }

    @GetMapping("/{id}")
    suspend fun getAccount(@PathVariable id: Long): AccountResponse {
        val account = getAccountBalanceUseCase.execute(id)
        return AccountResponse.from(account)
    }
}
