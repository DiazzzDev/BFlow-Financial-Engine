#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

RDS_PASSWORD=$(require_output RDS_PASSWORD)
RDS_ENDPOINT=$(require_output RDS_ENDPOINT)

SECRET_NAME="${PROJECT_NAME}/database"

create_or_update_secret() {

    local SECRET_ARN

    SECRET_ARN=$(aws secretsmanager describe-secret \
        --region "$AWS_REGION" \
        --secret-id "$SECRET_NAME" \
        --query "ARN" \
        --output text 2>/dev/null || true)


    SECRET_VALUE=$(jq -n \
    --arg username "$DB_USERNAME" \
    --arg password "$RDS_PASSWORD" \
    --arg host "$RDS_ENDPOINT" \
    --arg port "$DB_PORT" \
    --arg dbname "$DB_NAME" \
'{
    DB_USER: $DB_USER,
    DB_PASSWORD: $DB_PASSWORD,
    DB_HOST: $DB_HOST,
    DB_PORT: $DB_PORT,
    DB_NAME: $DB_NAME
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

    else

        echo "Updating existing secret..."

        aws secretsmanager put-secret-value \
            --region "$AWS_REGION" \
            --secret-id "$SECRET_NAME" \
            --secret-string "$SECRET_VALUE" >/dev/null

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