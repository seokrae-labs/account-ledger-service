package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.Account

interface AccountRepository {
    suspend fun save(account: Account): Account
    suspend fun findById(id: Long): Account?
    suspend fun findByIdForUpdate(id: Long): Account?
    suspend fun findByIdsForUpdate(ids: List<Long>): List<Account>
}
