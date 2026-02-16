-- Dead Letter Queue for failed transfer events
-- Stores events that couldn't be processed after retries

CREATE TABLE transfer_dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,      -- FAILURE_PERSISTENCE_FAILED, etc.
    payload JSONB NOT NULL,                -- Full context for manual recovery
    failure_reason TEXT,                   -- Last error message
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP NULL,
    processed BOOLEAN NOT NULL DEFAULT false,
    processed_at TIMESTAMP NULL
);

-- Index for idempotency lookup
CREATE INDEX idx_dlq_idempotency_key ON transfer_dead_letter_queue(idempotency_key);

-- Index for unprocessed events (batch recovery)
CREATE INDEX idx_dlq_unprocessed ON transfer_dead_letter_queue(processed, created_at DESC)
    WHERE processed = false;

-- Index for monitoring queries
CREATE INDEX idx_dlq_created_at ON transfer_dead_letter_queue(created_at DESC);

-- Comment
COMMENT ON TABLE transfer_dead_letter_queue IS 'Dead letter queue for transfer events that failed after retries';
COMMENT ON COLUMN transfer_dead_letter_queue.payload IS 'JSONB containing transfer context for manual recovery';
