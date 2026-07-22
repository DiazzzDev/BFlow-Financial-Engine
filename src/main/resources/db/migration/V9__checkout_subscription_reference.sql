ALTER TABLE subscriptions ADD COLUMN checkout_reference VARCHAR(100);

CREATE UNIQUE INDEX idx_unique_checkout_reference_active
    ON subscriptions (checkout_reference)
    WHERE status IN ('PENDING_ACTIVATION', 'ACTIVE', 'PAST_DUE');