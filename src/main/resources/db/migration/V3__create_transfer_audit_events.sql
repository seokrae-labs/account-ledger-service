-- Transfer Audit Events Table
-- Immutable, append-only log for all transfer lifecycle events

CREATE TABLE transfer_audit_events (
    id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NULL,          -- Nullable: main tx rollback may delete transfer row
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,  -- TRANSFER_COMPLETED, TRANSFER_FAILED_BUSINESS, etc.
    transfer_status VARCHAR(32) NULL,
    reason_code VARCHAR(128) NULL,
    reason_message TEXT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_audit_idempotency_key ON transfer_audit_events(idempotency_key);
CREATE INDEX idx_audit_transfer_id ON transfer_audit_events(transfer_id);
CREATE INDEX idx_audit_created_at ON transfer_audit_events(created_at DESC);
CREATE INDEX idx_audit_event_type ON transfer_audit_events(event_type);
