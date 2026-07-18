#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking AWS Budget..."

ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

BUDGET_EXISTS=$(aws budgets describe-budget \
    --account-id "$ACCOUNT_ID" \
    --budget-name "$BUDGET_NAME" \
    --query "Budget.BudgetName" \
    --output text 2>/dev/null || true)

if [[ "$BUDGET_EXISTS" != "$BUDGET_NAME" ]]; then

    echo "AWS Budget already deleted."

    exit 0

fi

echo "Deleting AWS Budget..."

aws budgets delete-budget \
    --account-id "$ACCOUNT_ID" \
    --budget-name "$BUDGET_NAME"

echo "AWS Budget deleted successfully."