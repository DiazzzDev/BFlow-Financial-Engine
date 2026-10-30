-- A user may prepare the next subscription while the current paid plan
-- remains active. Only one pending checkout per user and plan is allowed.
DROP INDEX IF EXISTS idx_unique_active_subscription;
CREATE UNIQUE INDEX idx_unique_pending_subscription_per_plan
    ON subscriptions (user_id, plan_id)
    WHERE status = 'PENDING_ACTIVATION';

-- Keep the single-active-plan invariant while allowing renewal checkout.
DROP INDEX IF EXISTS idx_unique_active_subscription_per_user;
CREATE UNIQUE INDEX idx_unique_active_subscription_per_user
    ON subscriptions (user_id)
    WHERE status = 'ACTIVE';

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (
    type IN (
        'BUDGET_SUCCESS', 'BUDGET_GROUP_SUCCESS', 'BUDGET_WARNING',
        'BUDGET_CRITICAL', 'BUDGET_EXCEEDED', 'GOAL_REACHED',
        'NEW_CONTRIBUTOR', 'ACCOUNT_LOCKED', 'WALLET_INVITATION',
        'SUBSCRIPTION_RENEWAL'
    )
);
