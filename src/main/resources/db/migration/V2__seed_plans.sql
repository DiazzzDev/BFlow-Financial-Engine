INSERT INTO plans (
    id,
    code,
    name,
    price,
    billing_period,
    max_wallets,
    max_budgets,
    max_recurring_transactions,
    max_shared_wallets,
    max_wallet_members,
    dashboard_customization,
    can_create_shared_wallets,
    export_enabled,
    active,
    created_at,
    updated_at
)
VALUES (
    gen_random_uuid(),
    'FREE',
    'Free Plan',
    0,
    'MONTHLY',
    1,
    3,
    10,
    0,
    1,
    false,
    false,
    false,
    true,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;