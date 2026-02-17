package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.DeadLetterEntry

/**
 * Repository interface for Dead Letter Queue persistence
 *
 * Design:
 * - Output port for hexagonal architecture
 * - Provides fallback persistence for failed events
 * - No transaction coordination needed (single INSERT)
 */
interface DeadLetterRepository {
    /**
     * Saves a dead letter entry
     *
     * @param entry The entry to save
     * @return Saved entry with generated ID
     */
    suspend fun save(entry: DeadLetterEntry): DeadLetterEntry

    /**
     * Finds an entry by idempotency key
     *
     * @param idempotencyKey The key to search for
     * @return Entry if found, null otherwise
     */
    suspend fun findByIdempotencyKey(idempotencyKey: String): DeadLetterEntry?
}
