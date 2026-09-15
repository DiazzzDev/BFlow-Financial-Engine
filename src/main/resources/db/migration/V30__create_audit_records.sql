CREATE TABLE audit_records (
                               id              UUID PRIMARY KEY,
                               user_id         UUID NOT NULL,
                               actor_type      VARCHAR(20) NOT NULL,
                               source          VARCHAR(20) NOT NULL,
                               http_method     VARCHAR(10) NOT NULL,
                               path            VARCHAR(255) NOT NULL,
                               resource_type   VARCHAR(50) NOT NULL,
                               http_status     INTEGER NOT NULL,
                               result          VARCHAR(10) NOT NULL,
                               correlation_id  VARCHAR(64),
                               created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The read path this backs (RepositoryAudit.findByUserIdOrderByCreatedAtDesc)
-- filters by user and sorts by recency — this index matches that access
-- pattern directly instead of relying on a full table scan.
CREATE INDEX idx_audit_records_user_created
    ON audit_records (user_id, created_at DESC);