package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Transfer

data class TransfersPage(
    val transfers: List<Transfer>,
    val totalElements: Long,
    val page: Int,
    val size: Int
)

interface GetTransfersUseCase {
    suspend fun execute(page: Int, size: Int): TransfersPage
}
