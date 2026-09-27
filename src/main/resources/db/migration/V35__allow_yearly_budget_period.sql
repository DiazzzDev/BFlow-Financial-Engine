ALTER TABLE budgets
    DROP CONSTRAINT budgets_period_check;

ALTER TABLE budgets
    ADD CONSTRAINT budgets_period_check
    CHECK (
        (period)::text = ANY (
            (ARRAY['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'])::text[]
        )
    );
