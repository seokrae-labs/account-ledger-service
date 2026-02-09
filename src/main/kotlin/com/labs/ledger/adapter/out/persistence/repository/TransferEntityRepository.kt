package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.TransferEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferEntityRepository : CoroutineCrudRepository<TransferEntity, Long> {
    suspend fun findByIdempotencyKey(idempotencyKey: String): TransferEntity?
}
