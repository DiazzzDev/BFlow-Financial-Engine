#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

delete_secret() {
    local SECRET_NAME="$1"

    echo "Checking Secrets Manager secret: $SECRET_NAME..."

    SECRET_EXISTS=$(aws secretsmanager describe-secret \
        --region "$AWS_REGION" \
        --secret-id "$SECRET_NAME" \
        --query "Name" \
        --output text 2>/dev/null || true)

    if [[ -z "$SECRET_EXISTS" || "$SECRET_EXISTS" == "None" ]]; then
        echo "Secret already deleted: $SECRET_NAME"
        return
    fi

    echo "Deleting secret: $SECRET_NAME..."
    aws secretsmanager delete-secret \
        --region "$AWS_REGION" \
        --secret-id "$SECRET_NAME" \
        --force-delete-without-recovery
}

delete_secret "${PROJECT_NAME}/database"
delete_secret "${PROJECT_NAME}/firebase"
delete_secret "${PROJECT_NAME}/wompi"


if [[ -f "$SCRIPT_DIR/../secrets.env" ]]; then

    echo "Removing local secrets file..."

    rm -f "$SCRIPT_DIR/../secrets.env"

fi

echo "Local secrets cleaned."
