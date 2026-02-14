package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.*
import com.labs.ledger.application.port.`in`.GetAccountsUseCase
import com.labs.ledger.application.port.`in`.GetLedgerEntriesUseCase
import com.labs.ledger.domain.port.CreateAccountUseCase
import com.labs.ledger.domain.port.DepositUseCase
import com.labs.ledger.domain.port.GetAccountBalanceUseCase
import com.labs.ledger.domain.port.UpdateAccountStatusUseCase
import com.labs.ledger.domain.model.AccountStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val createAccountUseCase: CreateAccountUseCase,
    private val depositUseCase: DepositUseCase,
    private val getAccountBalanceUseCase: GetAccountBalanceUseCase,
    private val updateAccountStatusUseCase: UpdateAccountStatusUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val getLedgerEntriesUseCase: GetLedgerEntriesUseCase
) {

    @GetMapping
    suspend fun getAccounts(
        @Valid @ModelAttribute pageRequest: com.labs.ledger.adapter.`in`.web.dto.PageRequest = com.labs.ledger.adapter.`in`.web.dto.PageRequest()
    ): PageResponse<AccountResponse> {
        val page = getAccountsUseCase.execute(pageRequest.page, pageRequest.size)
        val content = page.accounts.map { AccountResponse.from(it) }
        return PageResponse.of(content, page.page, page.size, page.totalElements)
    }

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

    @GetMapping("/{id}/ledger-entries")
    suspend fun getLedgerEntries(
        @PathVariable id: Long,
        @Valid @ModelAttribute pageRequest: com.labs.ledger.adapter.`in`.web.dto.PageRequest = com.labs.ledger.adapter.`in`.web.dto.PageRequest()
    ): PageResponse<LedgerEntryResponse> {
        val page = getLedgerEntriesUseCase.execute(id, pageRequest.page, pageRequest.size)
        val content = page.entries.map { LedgerEntryResponse.from(it) }
        return PageResponse.of(content, page.page, page.size, page.totalElements)
    }

    @PatchMapping("/{id}/status")
    suspend fun updateAccountStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateAccountStatusRequest
    ): AccountResponse {
        val targetStatus = try {
            AccountStatus.valueOf(request.status.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid status: ${request.status}. Valid values: ACTIVE, SUSPENDED, CLOSED")
        }

        val account = updateAccountStatusUseCase.execute(id, targetStatus)
        return AccountResponse.from(account)
    }
}
