-- Reset test database state
-- TRUNCATE is faster than DELETE and automatically handles CASCADE

TRUNCATE TABLE transfers CASCADE;
TRUNCATE TABLE ledger_entries CASCADE;
TRUNCATE TABLE accounts CASCADE;

-- Reset sequences for predictable test IDs
ALTER SEQUENCE accounts_id_seq RESTART WITH 1;
ALTER SEQUENCE transfers_id_seq RESTART WITH 1;
ALTER SEQUENCE ledger_entries_id_seq RESTART WITH 1;
