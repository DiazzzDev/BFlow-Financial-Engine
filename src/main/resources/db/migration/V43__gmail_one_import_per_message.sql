-- Existing imports remain untouched for auditability. New imports receive a
-- message-level key, enforcing one automated financial decision per email.
ALTER TABLE gmail_imports
    ADD COLUMN IF NOT EXISTS message_dedup_key VARCHAR(512);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gmail_import_message_dedup_key
    ON gmail_imports(message_dedup_key)
    WHERE message_dedup_key IS NOT NULL;
