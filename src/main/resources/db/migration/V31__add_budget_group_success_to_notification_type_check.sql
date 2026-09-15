-- The notifications.type check constraint (created inline in
-- V1__baseline.sql, auto-named notifications_type_check by
-- Postgres) never included BUDGET_GROUP_SUCCESS, even though that
-- value was added to NotificationType afterwards. Any attempt to
-- insert a BUDGET_GROUP_SUCCESS notification (e.g. from
-- POST /api/v1/expenses when a shared wallet stays within budget)
-- is rejected by Postgres with a check constraint violation.
--
-- Realign the constraint with bflow.notifications.enums.NotificationType.
ALTER TABLE notifications
    DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'BUDGET_SUCCESS',
            'BUDGET_GROUP_SUCCESS',
            'BUDGET_WARNING',
            'BUDGET_CRITICAL',
            'BUDGET_EXCEEDED',
            'GOAL_REACHED',
            'NEW_CONTRIBUTOR',
            'ACCOUNT_LOCKED'
        ));