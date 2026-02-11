package com.labs.ledger.adapter.out.persistence.adapter

import com.labs.ledger.adapter.out.persistence.entity.AccountEntity
import com.labs.ledger.adapter.out.persistence.repository.AccountEntityRepository
import com.labs.ledger.domain.exception.OptimisticLockException
import com.labs.ledger.domain.model.Account
import com.labs.ledger.domain.model.AccountStatus
import com.labs.ledger.domain.port.AccountRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component

@Component
class AccountPersistenceAdapter(
    private val repository: AccountEntityRepository
) : AccountRepository {

    override suspend fun save(account: Account): Account {
        return try {
            val entity = toEntity(account)
            val saved = repository.save(entity)
            toDomain(saved)
        } catch (e: OptimisticLockingFailureException) {
            throw OptimisticLockException("Optimistic lock failed for account: ${account.id}")
        }
    }

    override suspend fun findById(id: Long): Account? {
        return repository.findById(id)?.let { toDomain(it) }
    }

    override suspend fun findByIdForUpdate(id: Long): Account? {
        return repository.findByIdForUpdate(id)?.let { toDomain(it) }
    }

    override suspend fun findByIdsForUpdate(ids: List<Long>): List<Account> {
        return repository.findByIdsForUpdate(ids).map { toDomain(it) }
    }

    private fun toEntity(domain: Account): AccountEntity {
        return AccountEntity(
            id = domain.id,
            ownerName = domain.ownerName,
            balance = domain.balance,
            status = domain.status.name,
            version = domain.version,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun toDomain(entity: AccountEntity): Account {
        return Account(
            id = entity.id,
            ownerName = entity.ownerName,
            balance = entity.balance,
            status = AccountStatus.valueOf(entity.status),
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
