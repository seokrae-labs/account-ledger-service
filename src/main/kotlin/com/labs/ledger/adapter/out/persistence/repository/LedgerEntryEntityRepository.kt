package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LedgerEntryEntityRepository : CoroutineCrudRepository<LedgerEntryEntity, Long> {
    fun findByAccountId(accountId: Long): Flow<LedgerEntryEntity>
}
