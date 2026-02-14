package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LedgerEntryEntityRepository : CoroutineCrudRepository<LedgerEntryEntity, Long> {
    fun findByAccountId(accountId: Long): Flow<LedgerEntryEntity>

    @Query("""
        SELECT * FROM ledger_entry
        WHERE account_id = :accountId
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findByAccountIdWithPagination(accountId: Long, offset: Long, limit: Int): Flow<LedgerEntryEntity>

    @Query("SELECT COUNT(*) FROM ledger_entry WHERE account_id = :accountId")
    suspend fun countByAccountId(accountId: Long): Long
}
