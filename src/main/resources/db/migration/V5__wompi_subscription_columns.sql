-- === payments ===
ALTER TABLE payments RENAME COLUMN external_transaction_id TO provider_payment_id;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS reference VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotency_key UUID;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE payments ADD CONSTRAINT uq_payments_reference UNIQUE (reference);
ALTER TABLE payments ADD CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key);

DO $$
DECLARE con record;
BEGIN
    FOR con IN SELECT conname FROM pg_constraint WHERE conrelid = 'payments'::regclass AND contype = 'c' AND conname LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', con.conname);
    END LOOP;
END $$;

ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','CANCELED','REFUNDED'));

UPDATE payments SET status = 'SUCCEEDED' WHERE status = 'PAID';

-- === subscriptions ===
ALTER TABLE subscriptions RENAME COLUMN current_price TO billing_amount;
ALTER TABLE subscriptions ALTER COLUMN ends_at DROP NOT NULL;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;

DO $$
DECLARE con record;
BEGIN
    FOR con IN SELECT conname FROM pg_constraint WHERE conrelid = 'subscriptions'::regclass AND contype = 'c' AND conname LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE subscriptions DROP CONSTRAINT %I', con.conname);
    END LOOP;
END $$;

ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check
    CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','EXPIRED','CANCELED','PAST_DUE'));

-- === plans ===
ALTER TABLE plans ADD COLUMN IF NOT EXISTS provider_link_id VARCHAR(255);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS checkout_url VARCHAR(500);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS billing_day INTEGER;