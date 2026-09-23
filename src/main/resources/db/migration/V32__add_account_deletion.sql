ALTER TABLE users
    ADD COLUMN deletion_requested_at TIMESTAMP NULL;

CREATE INDEX idx_users_pending_deletion
    ON users (status, deletion_requested_at)
    WHERE status = 'PENDING_DELETION';

ALTER TABLE users
    ADD COLUMN is_system_account BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE subscriptions
    ADD COLUMN billing_email VARCHAR(255) NULL;

ALTER TABLE payments
    ADD COLUMN billing_email VARCHAR(255) NULL;

-- Usuario fantasma: absorbe contribuciones de wallets compartidas y
-- registros de facturación de cuentas con hard delete.
INSERT INTO users (
    id, email, name, name_source, picture_source, language,
    email_verified, status, is_system_account, created_at, updated_at
) VALUES (
             '00000000-0000-0000-0000-000000000001',
             'system+ghost@bflow-studio.com',
             'Usuario de Bflow',
             'USER',
             'NONE',
             'ES',
             true,
             'ACTIVE',
             true,
             now(),
             now()
         );