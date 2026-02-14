package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Transfer

interface TransferRepository {
    suspend fun save(transfer: Transfer): Transfer
    suspend fun findByIdempotencyKey(idempotencyKey: String): Transfer?

    // Pagination support
    suspend fun findAll(offset: Long, limit: Int): List<Transfer>
    suspend fun count(): Long
}
