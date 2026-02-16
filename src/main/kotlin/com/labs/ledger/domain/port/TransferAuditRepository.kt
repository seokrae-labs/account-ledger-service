package com.labs.ledger.domain.port

import com.labs.ledger.domain.model.TransferAuditEvent

/**
 * Port for persisting transfer audit events
 *
 * Design:
 * - Append-only: only save operation (no update/delete)
 * - Independent transaction: survives main transfer transaction rollback
 */
interface TransferAuditRepository {
    /**
     * Persist an audit event
     *
     * @param event The audit event to persist
     * @return The persisted event with generated ID
     */
    suspend fun save(event: TransferAuditEvent): TransferAuditEvent
}
