CREATE UNIQUE INDEX idx_unique_active_subscription
ON subscriptions (user_id, plan_id)
WHERE status IN ('ACTIVE', 'PENDING_ACTIVATION');