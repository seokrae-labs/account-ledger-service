package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.*
import com.labs.ledger.domain.port.GetTransfersUseCase
import com.labs.ledger.domain.port.TransferUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferUseCase: TransferUseCase,
    private val getTransfersUseCase: GetTransfersUseCase
) {

    @GetMapping
    suspend fun getTransfers(
        @Valid @ModelAttribute pageRequest: com.labs.ledger.adapter.`in`.web.dto.PageRequest = com.labs.ledger.adapter.`in`.web.dto.PageRequest()
    ): PageResponse<TransferResponse> {
        val page = getTransfersUseCase.execute(pageRequest.page, pageRequest.size)
        val content = page.transfers.map { TransferResponse.from(it) }
        return PageResponse.of(content, page.page, page.size, page.totalElements)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun transfer(
        @Valid @RequestBody request: TransferRequest,
        exchange: ServerWebExchange
    ): TransferResponse {
        val idempotencyKey = exchange.request.headers.getFirst("Idempotency-Key")
            ?: throw IllegalArgumentException("Idempotency-Key header is required")

        if (idempotencyKey.length > 255) {
            throw IllegalArgumentException("Idempotency-Key must not exceed 255 characters")
        }

        val transfer = transferUseCase.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = request.fromAccountId!!,
            toAccountId = request.toAccountId!!,
            amount = request.amount,
            description = request.description
        )

        return TransferResponse.from(transfer)
    }
}
