package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.TransferEntity
import com.labs.ledger.adapter.out.persistence.repository.TransferEntityRepository
import com.labs.ledger.domain.model.Transfer
import com.labs.ledger.domain.model.TransferStatus
import com.labs.ledger.domain.port.TransferRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component

@Component
class TransferPersistenceAdapter(
    private val repository: TransferEntityRepository
) : TransferRepository {

    override suspend fun save(transfer: Transfer): Transfer {
        val entity = toEntity(transfer)
        val saved = repository.save(entity)
        return toDomain(saved)
    }

    override suspend fun findByIdempotencyKey(idempotencyKey: String): Transfer? {
        return repository.findByIdempotencyKey(idempotencyKey)?.let { toDomain(it) }
    }

    override suspend fun findAll(offset: Long, limit: Int): List<Transfer> {
        return repository.findAllWithPagination(offset, limit)
            .map { toDomain(it) }
            .toList()
    }

    override suspend fun count(): Long {
        return repository.count()
    }

    private fun toEntity(domain: Transfer): TransferEntity {
        return TransferEntity(
            id = domain.id,
            idempotencyKey = domain.idempotencyKey,
            fromAccountId = domain.fromAccountId,
            toAccountId = domain.toAccountId,
            amount = domain.amount,
            status = domain.status.name,
            description = domain.description,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun toDomain(entity: TransferEntity): Transfer {
        return Transfer(
            id = entity.id,
            idempotencyKey = entity.idempotencyKey,
            fromAccountId = entity.fromAccountId,
            toAccountId = entity.toAccountId,
            amount = entity.amount,
            status = TransferStatus.valueOf(entity.status),
            description = entity.description,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
