-- Drop tables if exists (for development)
DROP TABLE IF EXISTS transfer_dead_letter_queue CASCADE;
DROP TABLE IF EXISTS transfer_audit_events CASCADE;
DROP TABLE IF EXISTS ledger_entries CASCADE;
DROP TABLE IF EXISTS transfers CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;

-- Accounts table with optimistic locking
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_status ON accounts(status);

-- Ledger entries (append-only audit log)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    reference_id VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT check_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at DESC);

-- Transfers table with idempotency key
CREATE TABLE transfers (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_from_account FOREIGN KEY (from_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transfer_to_account FOREIGN KEY (to_account_id) REFERENCES accounts(id),
    CONSTRAINT check_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT check_different_accounts CHECK (from_account_id != to_account_id)
);

CREATE INDEX idx_transfers_idempotency_key ON transfers(idempotency_key);
CREATE INDEX idx_transfers_from_account_id ON transfers(from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers(to_account_id);
CREATE INDEX idx_transfers_status ON transfers(status);

-- Transfer Audit Events Table (append-only audit log)
CREATE TABLE transfer_audit_events (
    id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    transfer_status VARCHAR(32) NULL,
    reason_code VARCHAR(128) NULL,
    reason_message TEXT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_idempotency_key ON transfer_audit_events(idempotency_key);
CREATE INDEX idx_audit_transfer_id ON transfer_audit_events(transfer_id);
CREATE INDEX idx_audit_created_at ON transfer_audit_events(created_at DESC);
CREATE INDEX idx_audit_event_type ON transfer_audit_events(event_type);

-- Dead Letter Queue
CREATE TABLE transfer_dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP NULL,
    processed BOOLEAN NOT NULL DEFAULT false,
    processed_at TIMESTAMP NULL
);

CREATE INDEX idx_dlq_idempotency_key ON transfer_dead_letter_queue(idempotency_key);
CREATE INDEX idx_dlq_unprocessed ON transfer_dead_letter_queue(processed, created_at DESC);
CREATE INDEX idx_dlq_created_at ON transfer_dead_letter_queue(created_at DESC);
