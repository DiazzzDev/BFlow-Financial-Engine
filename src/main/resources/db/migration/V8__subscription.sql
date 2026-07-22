ALTER TABLE subscriptions ADD COLUMN provider_subscriber_id VARCHAR(255);

CREATE UNIQUE INDEX idx_unique_provider_subscriber_active
ON subscriptions (provider_subscriber_id)
WHERE status IN ('ACTIVE', 'PENDING_ACTIVATION', 'PAST_DUE');