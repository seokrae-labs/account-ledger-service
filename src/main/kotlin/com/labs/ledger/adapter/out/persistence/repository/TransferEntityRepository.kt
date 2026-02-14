package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.TransferEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferEntityRepository : CoroutineCrudRepository<TransferEntity, Long> {
    suspend fun findByIdempotencyKey(idempotencyKey: String): TransferEntity?

    @Query("""
        SELECT * FROM transfers
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllWithPagination(offset: Long, limit: Int): Flow<TransferEntity>
}
