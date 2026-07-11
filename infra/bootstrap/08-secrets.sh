#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

if [[ -f "$SCRIPT_DIR/../secrets.env" ]]; then
    source "$SCRIPT_DIR/../secrets.env"
fi

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

RDS_ENDPOINT=$(require_output RDS_ENDPOINT)

SECRET_NAME="${PROJECT_NAME}/database"

create_or_update_secret() {

    local SECRET_ARN

    if aws secretsmanager describe-secret \
        --region "$AWS_REGION" \
        --secret-id "$SECRET_NAME" >/dev/null 2>&1; then

        SECRET_ARN=$(aws secretsmanager describe-secret \
            --region "$AWS_REGION" \
            --secret-id "$SECRET_NAME" \
            --query ARN \
            --output text)

    else
        SECRET_ARN=""
    fi

    if [[ -n "$SECRET_ARN" && "$SECRET_ARN" != "None" ]]; then

        echo "Secret already exists."

        append_output "RDS_SECRET_ARN" "$SECRET_ARN"

        return

    fi

    if [[ -z "${RDS_PASSWORD:-}" ]]; then
        echo "Cannot create secret."
        echo "RDS_PASSWORD not found in secrets.env"
        exit 1
    fi

    SECRET_VALUE=$(jq -n \
        --arg username "$DB_USERNAME" \
        --arg password "$RDS_PASSWORD" \
        --arg host "$RDS_ENDPOINT" \
        --arg port "$DB_PORT" \
        --arg dbname "$DB_NAME" \
    '{
        "DB_USER": $username,
        "DB_PASSWORD": $password,
        "DB_HOST": $host,
        "DB_PORT": $port,
        "DB_NAME": $dbname
    }')

    if [[ -z "$SECRET_ARN" || "$SECRET_ARN" == "None" ]]; then

        echo "Creating secret..."

        SECRET_ARN=$(aws secretsmanager create-secret \
            --region "$AWS_REGION" \
            --name "$SECRET_NAME" \
            --description "Database credentials for ${PROJECT_NAME}" \
            --secret-string "$SECRET_VALUE" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
            --query "ARN" \
            --output text)

    fi

    append_output "RDS_SECRET_ARN" "$SECRET_ARN"

}

remove_password_from_outputs() {

    grep -v "^RDS_PASSWORD=" "$OUTPUT_FILE" \
        > "${OUTPUT_FILE}.tmp" || true

    mv "${OUTPUT_FILE}.tmp" "$OUTPUT_FILE"

}

create_or_update_secret

remove_password_from_outputs

echo "Secret ready."