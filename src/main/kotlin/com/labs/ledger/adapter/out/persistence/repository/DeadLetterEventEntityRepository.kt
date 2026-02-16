package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.DeadLetterEventEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface DeadLetterEventEntityRepository : CoroutineCrudRepository<DeadLetterEventEntity, Long> {

    @Query("""
        SELECT * FROM transfer_dead_letter_queue
        WHERE processed = false
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    fun findUnprocessed(limit: Int): Flow<DeadLetterEventEntity>

    @Query("""
        UPDATE transfer_dead_letter_queue
        SET processed = true, processed_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """)
    suspend fun markProcessed(id: Long)

    @Query("""
        SELECT COUNT(*) FROM transfer_dead_letter_queue
        WHERE processed = false
    """)
    suspend fun countUnprocessed(): Long

    suspend fun findByIdempotencyKey(idempotencyKey: String): DeadLetterEventEntity?
}
