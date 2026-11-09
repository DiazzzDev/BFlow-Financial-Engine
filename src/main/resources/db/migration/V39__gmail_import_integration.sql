CREATE TABLE gmail_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    encrypted_refresh_token TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONNECTED'
        CHECK (status IN ('CONNECTED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE gmail_oauth_states (
    state_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX idx_gmail_oauth_states_expiry ON gmail_oauth_states(expires_at);

CREATE TABLE gmail_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    integration_id UUID NOT NULL REFERENCES gmail_integrations(id) ON DELETE CASCADE,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    message_id VARCHAR(255) NOT NULL,
    attachment_id VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    expense_id UUID REFERENCES expenses(id) ON DELETE SET NULL,
    transaction_id UUID,
    transaction_type VARCHAR(20) CHECK (transaction_type IN ('EXPENSE', 'INCOME')),
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gmail_import_message_attachment
        UNIQUE (user_id, message_id, attachment_id)
);
CREATE INDEX idx_gmail_import_user_status
    ON gmail_imports(user_id, status, created_at DESC);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('BUDGET_SUCCESS', 'BUDGET_GROUP_SUCCESS',
        'BUDGET_WARNING', 'BUDGET_CRITICAL', 'BUDGET_EXCEEDED',
        'GOAL_REACHED', 'NEW_CONTRIBUTOR', 'ACCOUNT_LOCKED',
        'WALLET_INVITATION', 'SUBSCRIPTION_RENEWAL', 'EXPENSE_REGISTERED'));
