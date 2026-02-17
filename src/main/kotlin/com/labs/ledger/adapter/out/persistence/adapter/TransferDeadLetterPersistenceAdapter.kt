package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.TransferDeadLetterEntity
import com.labs.ledger.adapter.out.persistence.repository.TransferDeadLetterEntityRepository
import com.labs.ledger.domain.model.DeadLetterEntry
import com.labs.ledger.domain.model.DeadLetterEventType
import com.labs.ledger.domain.port.DeadLetterRepository
import io.r2dbc.postgresql.codec.Json
import org.springframework.stereotype.Component

/**
 * Adapter for Dead Letter Queue persistence
 *
 * Design:
 * - Implements hexagonal architecture output port
 * - Maps between domain models and R2DBC entities
 * - No transaction coordination needed (single INSERT operations)
 * - Converts String payload to/from PostgreSQL JSONB using R2DBC Json type
 */
@Component
class TransferDeadLetterPersistenceAdapter(
    private val repository: TransferDeadLetterEntityRepository
) : DeadLetterRepository {

    override suspend fun save(entry: DeadLetterEntry): DeadLetterEntry {
        val entity = toEntity(entry)
        val saved = repository.save(entity)
        return toDomain(saved)
    }

    override suspend fun findByIdempotencyKey(idempotencyKey: String): DeadLetterEntry? {
        return repository.findByIdempotencyKey(idempotencyKey)?.let { toDomain(it) }
    }

    private fun toEntity(domain: DeadLetterEntry): TransferDeadLetterEntity {
        return TransferDeadLetterEntity(
            id = domain.id,
            idempotencyKey = domain.idempotencyKey,
            eventType = domain.eventType.name,
            payload = Json.of(domain.payload),  // String -> Json
            failureReason = domain.failureReason,
            retryCount = domain.retryCount,
            createdAt = domain.createdAt,
            lastRetryAt = domain.lastRetryAt,
            processed = domain.processed,
            processedAt = domain.processedAt
        )
    }

    private fun toDomain(entity: TransferDeadLetterEntity): DeadLetterEntry {
        return DeadLetterEntry(
            id = entity.id,
            idempotencyKey = entity.idempotencyKey,
            eventType = DeadLetterEventType.valueOf(entity.eventType),
            payload = entity.payload.asString(),  // Json -> String
            failureReason = entity.failureReason,
            retryCount = entity.retryCount,
            createdAt = entity.createdAt,
            lastRetryAt = entity.lastRetryAt,
            processed = entity.processed,
            processedAt = entity.processedAt
        )
    }
}
