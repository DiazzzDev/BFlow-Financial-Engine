ALTER TABLE notifications ADD COLUMN reference_id uuid;

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
       'BUDGET_SUCCESS','BUDGET_GROUP_SUCCESS','BUDGET_WARNING','BUDGET_CRITICAL',
        'BUDGET_EXCEEDED','GOAL_REACHED','NEW_CONTRIBUTOR','ACCOUNT_LOCKED',
        'WALLET_INVITATION'
));

ALTER TABLE wallet_users
    ADD COLUMN is_default boolean NOT NULL DEFAULT false;

UPDATE wallet_users
SET is_default = true
WHERE id IN (
    SELECT DISTINCT ON (user_id) id
FROM wallet_users
WHERE role = 'OWNER'
ORDER BY user_id, created_at ASC, id ASC
    );

CREATE UNIQUE INDEX uq_wallet_users_one_default
    ON wallet_users (user_id)
    WHERE is_default;
