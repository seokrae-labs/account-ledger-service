package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.TransferRequest
import com.labs.ledger.adapter.`in`.web.dto.TransferResponse
import com.labs.ledger.domain.port.TransferUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferUseCase: TransferUseCase
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun transfer(
        @RequestBody request: TransferRequest,
        exchange: ServerWebExchange
    ): TransferResponse {
        val idempotencyKey = exchange.request.headers.getFirst("Idempotency-Key")
            ?: throw IllegalArgumentException("Idempotency-Key header is required")

        val transfer = transferUseCase.execute(
            idempotencyKey = idempotencyKey,
            fromAccountId = request.fromAccountId,
            toAccountId = request.toAccountId,
            amount = request.amount,
            description = request.description
        )

        return TransferResponse.from(transfer)
    }
}
