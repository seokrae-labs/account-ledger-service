package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.DeadLetterEventEntity
import com.labs.ledger.adapter.out.persistence.repository.DeadLetterEventEntityRepository
import com.labs.ledger.domain.model.DeadLetterEvent
import com.labs.ledger.domain.model.DeadLetterEventType
import com.labs.ledger.domain.port.DeadLetterQueueRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component

@Component
class DeadLetterQueuePersistenceAdapter(
    private val repository: DeadLetterEventEntityRepository
) : DeadLetterQueueRepository {

    override suspend fun save(event: DeadLetterEvent): DeadLetterEvent {
        val entity = toEntity(event)
        val saved = repository.save(entity)
        return toDomain(saved)
    }

    override suspend fun findUnprocessed(limit: Int): List<DeadLetterEvent> {
        return repository.findUnprocessed(limit)
            .map { toDomain(it) }
            .toList()
    }

    override suspend fun markProcessed(id: Long) {
        repository.markProcessed(id)
    }

    override suspend fun countUnprocessed(): Long {
        return repository.countUnprocessed()
    }

    override suspend fun findByIdempotencyKey(idempotencyKey: String): DeadLetterEvent? {
        return repository.findByIdempotencyKey(idempotencyKey)?.let { toDomain(it) }
    }

    private fun toEntity(domain: DeadLetterEvent): DeadLetterEventEntity {
        return DeadLetterEventEntity(
            id = domain.id,
            idempotencyKey = domain.idempotencyKey,
            eventType = domain.eventType.name,
            payload = domain.payload,
            failureReason = domain.failureReason,
            retryCount = domain.retryCount,
            createdAt = domain.createdAt,
            lastRetryAt = domain.lastRetryAt,
            processed = domain.processed,
            processedAt = domain.processedAt
        )
    }

    private fun toDomain(entity: DeadLetterEventEntity): DeadLetterEvent {
        return DeadLetterEvent(
            id = entity.id,
            idempotencyKey = entity.idempotencyKey,
            eventType = DeadLetterEventType.valueOf(entity.eventType),
            payload = entity.payload,
            failureReason = entity.failureReason,
            retryCount = entity.retryCount,
            createdAt = entity.createdAt,
            lastRetryAt = entity.lastRetryAt,
            processed = entity.processed,
            processedAt = entity.processedAt
        )
    }
}
