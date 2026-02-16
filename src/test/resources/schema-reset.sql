-- Reset test database state
-- TRUNCATE with CASCADE for clean reset (sequential test execution enforced)

TRUNCATE TABLE transfer_audit_events CASCADE;
TRUNCATE TABLE ledger_entries CASCADE;
TRUNCATE TABLE transfers CASCADE;
TRUNCATE TABLE accounts CASCADE;

-- Reset sequences for predictable test IDs
ALTER SEQUENCE accounts_id_seq RESTART WITH 1;
ALTER SEQUENCE transfers_id_seq RESTART WITH 1;
ALTER SEQUENCE ledger_entries_id_seq RESTART WITH 1;
ALTER SEQUENCE transfer_audit_events_id_seq RESTART WITH 1;
