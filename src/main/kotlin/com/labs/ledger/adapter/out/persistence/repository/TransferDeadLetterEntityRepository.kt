package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.TransferDeadLetterEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

/**
 * R2DBC repository for transfer_dead_letter_queue table
 */
@Repository
interface TransferDeadLetterEntityRepository : CoroutineCrudRepository<TransferDeadLetterEntity, Long> {
    /**
     * Finds entry by idempotency key
     */
    suspend fun findByIdempotencyKey(idempotencyKey: String): TransferDeadLetterEntity?
}
