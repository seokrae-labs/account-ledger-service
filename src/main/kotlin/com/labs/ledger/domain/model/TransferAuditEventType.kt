package com.labs.ledger.domain.model

/**
 * Transfer lifecycle event types for audit trail
 */
enum class TransferAuditEventType {
    /**
     * Transfer completed successfully
     */
    TRANSFER_COMPLETED,

    /**
     * Transfer failed due to business rule violation
     * (e.g., insufficient balance, invalid account)
     */
    TRANSFER_FAILED_BUSINESS,

    /**
     * Transfer failed due to system error
     * (e.g., database error, network timeout)
     */
    TRANSFER_FAILED_SYSTEM
}
