-- Add failure_reason column to transfers table for tracking failure details
ALTER TABLE transfers
ADD COLUMN failure_reason TEXT;

-- Add index for querying failed transfers
CREATE INDEX idx_transfers_failure_reason ON transfers(failure_reason) WHERE failure_reason IS NOT NULL;

COMMENT ON COLUMN transfers.failure_reason IS 'Records the reason for transfer failure when status is FAILED';
