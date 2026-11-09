ALTER TABLE gmail_integrations
    ADD COLUMN wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL;

CREATE INDEX idx_gmail_integrations_auto_sync
    ON gmail_integrations(status, wallet_id)
    WHERE wallet_id IS NOT NULL;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('BUDGET_SUCCESS', 'BUDGET_GROUP_SUCCESS',
        'BUDGET_WARNING', 'BUDGET_CRITICAL', 'BUDGET_EXCEEDED',
        'GOAL_REACHED', 'NEW_CONTRIBUTOR', 'ACCOUNT_LOCKED',
        'WALLET_INVITATION', 'SUBSCRIPTION_RENEWAL', 'EXPENSE_REGISTERED',
        'INCOME_REGISTERED'));
