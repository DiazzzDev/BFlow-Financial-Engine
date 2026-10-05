ALTER TABLE recurring_transactions
    DROP CONSTRAINT IF EXISTS recurring_transactions_frequency_check;

ALTER TABLE recurring_transactions
    ADD CONSTRAINT recurring_transactions_frequency_check
    CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'));
