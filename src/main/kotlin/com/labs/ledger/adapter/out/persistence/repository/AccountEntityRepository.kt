package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountEntityRepository : CoroutineCrudRepository<AccountEntity, Long> {

    @Query("SELECT * FROM accounts WHERE id = :id FOR UPDATE")
    suspend fun findByIdForUpdate(id: Long): AccountEntity?

    @Query("""
        SELECT * FROM accounts
        WHERE id IN (:ids)
        ORDER BY id
        FOR UPDATE
    """)
    suspend fun findByIdsForUpdate(ids: Collection<Long>): List<AccountEntity>

    @Query("""
        SELECT * FROM accounts
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllWithPagination(offset: Long, limit: Int): Flow<AccountEntity>
}
