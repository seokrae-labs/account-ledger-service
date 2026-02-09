package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.LedgerEntryEntity
import com.labs.ledger.adapter.out.persistence.repository.LedgerEntryEntityRepository
import com.labs.ledger.domain.model.LedgerEntry
import com.labs.ledger.domain.model.LedgerEntryType
import com.labs.ledger.domain.port.LedgerEntryRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component

@Component
class LedgerEntryPersistenceAdapter(
    private val repository: LedgerEntryEntityRepository
) : LedgerEntryRepository {

    override suspend fun save(entry: LedgerEntry): LedgerEntry {
        val entity = toEntity(entry)
        val saved = repository.save(entity)
        return toDomain(saved)
    }

    override suspend fun saveAll(entries: List<LedgerEntry>): List<LedgerEntry> {
        val entities = entries.map { toEntity(it) }
        return repository.saveAll(entities).toList().map { toDomain(it) }
    }

    override suspend fun findByAccountId(accountId: Long): List<LedgerEntry> {
        return repository.findByAccountId(accountId)
            .map { toDomain(it) }
            .toList()
    }

    private fun toEntity(domain: LedgerEntry): LedgerEntryEntity {
        return LedgerEntryEntity(
            id = domain.id,
            accountId = domain.accountId,
            type = domain.type.name,
            amount = domain.amount,
            referenceId = domain.referenceId,
            description = domain.description,
            createdAt = domain.createdAt
        )
    }

    private fun toDomain(entity: LedgerEntryEntity): LedgerEntry {
        return LedgerEntry(
            id = entity.id,
            accountId = entity.accountId,
            type = LedgerEntryType.valueOf(entity.type),
            amount = entity.amount,
            referenceId = entity.referenceId,
            description = entity.description,
            createdAt = entity.createdAt
        )
    }
}
