CREATE TABLE notification_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_device_tokens_platform_check
        CHECK (platform IN ('WEB', 'ANDROID', 'IOS'))
);

CREATE UNIQUE INDEX uq_notification_device_tokens_token
    ON notification_device_tokens (token);

CREATE INDEX idx_notification_device_tokens_user_enabled
    ON notification_device_tokens (user_id, enabled);
