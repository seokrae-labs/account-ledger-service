package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.TransferAuditEventEntity
import com.labs.ledger.adapter.out.persistence.repository.TransferAuditEventEntityRepository
import com.labs.ledger.domain.model.TransferAuditEvent
import com.labs.ledger.domain.model.TransferAuditEventType
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.TransferAuditRepository
import org.springframework.stereotype.Component

@Component
class TransferAuditPersistenceAdapter(
    private val repository: TransferAuditEventEntityRepository
) : TransferAuditRepository {

    override suspend fun save(event: TransferAuditEvent): TransferAuditEvent {
        val entity = toEntity(event)
        val saved = repository.save(entity)
        return toDomain(saved)
    }

    private fun toEntity(domain: TransferAuditEvent): TransferAuditEventEntity {
        return TransferAuditEventEntity(
            id = domain.id,
            transferId = domain.transferId,
            idempotencyKey = domain.idempotencyKey,
            eventType = domain.eventType.name,
            transferStatus = domain.transferStatus?.name,
            reasonCode = domain.reasonCode,
            reasonMessage = domain.reasonMessage,
            metadata = domain.metadata,
            createdAt = domain.createdAt
        )
    }

    private fun toDomain(entity: TransferAuditEventEntity): TransferAuditEvent {
        return TransferAuditEvent(
            id = entity.id,
            transferId = entity.transferId,
            idempotencyKey = entity.idempotencyKey,
            eventType = TransferAuditEventType.valueOf(entity.eventType),
            transferStatus = entity.transferStatus?.let { TransferStatus.valueOf(it) },
            reasonCode = entity.reasonCode,
            reasonMessage = entity.reasonMessage,
            metadata = entity.metadata,
            createdAt = entity.createdAt
        )
    }
}
