ALTER TABLE gmail_imports
    ADD COLUMN IF NOT EXISTS transaction_id UUID;

ALTER TABLE gmail_imports
    ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(20)
        CHECK (transaction_type IN ('EXPENSE', 'INCOME'));
