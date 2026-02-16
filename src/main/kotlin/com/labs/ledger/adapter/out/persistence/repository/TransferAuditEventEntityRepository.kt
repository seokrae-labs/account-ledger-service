package com.labs.ledger.adapter.out.persistence.repository

import com.labs.ledger.adapter.out.persistence.entity.TransferAuditEventEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferAuditEventEntityRepository : CoroutineCrudRepository<TransferAuditEventEntity, Long> {
    suspend fun findByIdempotencyKey(idempotencyKey: String): TransferAuditEventEntity?
}
