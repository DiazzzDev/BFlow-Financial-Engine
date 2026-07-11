#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

echo "Checking AWS budget..."

BUDGET_EXISTS=$(aws budgets describe-budget \
    --account-id "$(aws sts get-caller-identity --query Account --output text)" \
    --budget-name "$BUDGET_NAME" \
    --query "Budget.BudgetName" \
    --output text 2>/dev/null || true)


if [[ "$BUDGET_EXISTS" == "$BUDGET_NAME" ]]; then

    echo "Budget already exists."

    exit 0

fi

ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

echo "Creating AWS budget..."

aws budgets create-budget \
    --account-id "$ACCOUNT_ID" \
    --budget '{
        "BudgetName": "'"$BUDGET_NAME"'",
        "BudgetLimit": {
            "Amount": "'"$BUDGET_LIMIT_USD"'",
            "Unit": "USD"
        },
        "BudgetType": "COST",
        "TimeUnit": "MONTHLY",
        "CostFilters": {},
        "CostTypes": {
            "IncludeTax": true,
            "IncludeSubscription": true,
            "UseBlended": false,
            "IncludeRefund": false,
            "IncludeCredit": false,
            "IncludeUpfront": true,
            "IncludeRecurring": true,
            "IncludeOtherSubscription": true,
            "IncludeSupport": true,
            "IncludeDiscount": true,
            "UseAmortized": false
        }
    }' \
    --notifications-with-subscribers "$(cat <<EOF
[
    {
        "Notification": {
            "NotificationType": "ACTUAL",
            "ComparisonOperator": "GREATER_THAN",
            "Threshold": 50,
            "ThresholdType": "PERCENTAGE"
        },
        "Subscribers": [
            {
                "SubscriptionType": "EMAIL",
                "Address": "$BUDGET_EMAIL"
            }
        ]
    },
    {
        "Notification": {
            "NotificationType": "ACTUAL",
            "ComparisonOperator": "GREATER_THAN",
            "Threshold": 80,
            "ThresholdType": "PERCENTAGE"
        },
        "Subscribers": [
            {
                "SubscriptionType": "EMAIL",
                "Address": "$BUDGET_EMAIL"
            }
        ]
    },
    {
        "Notification": {
            "NotificationType": "ACTUAL",
            "ComparisonOperator": "GREATER_THAN",
            "Threshold": 100,
            "ThresholdType": "PERCENTAGE"
        },
        "Subscribers": [
            {
                "SubscriptionType": "EMAIL",
                "Address": "$BUDGET_EMAIL"
            }
        ]
    }
]
EOF
)"

append_output "AWS_BUDGET_NAME" "$BUDGET_NAME"

echo "AWS budget created successfully."