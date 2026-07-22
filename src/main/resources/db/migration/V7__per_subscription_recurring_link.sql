ALTER TABLE subscriptions ADD COLUMN provider_link_id VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN checkout_url VARCHAR(500);
ALTER TABLE subscriptions ADD COLUMN billing_day INTEGER;

-- evita reconciliar/matchear contra el enlace equivocado si dos
-- suscripciones (de distinto usuario) llegaran a compartir estado activo
CREATE UNIQUE INDEX idx_unique_provider_link_active
ON subscriptions (provider_link_id)
WHERE status IN ('ACTIVE', 'PENDING_ACTIVATION', 'PAST_DUE');