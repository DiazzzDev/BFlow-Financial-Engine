#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

if [[ -f "$SCRIPT_DIR/../secrets.env" ]]; then
    source "$SCRIPT_DIR/../secrets.env"
fi

if [[ -f "$SCRIPT_DIR/../supabase.env" ]]; then
    source "$SCRIPT_DIR/../supabase.env"
fi

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

DB_PROVIDER="${DB_PROVIDER:-rds}"

SECRET_NAME="${PROJECT_NAME}/database"

resolve_connection_values() {

    if [[ "$DB_PROVIDER" == "supabase" ]]; then

        echo "DB_PROVIDER=supabase — reading infra/supabase.env"

        for VAR in SUPABASE_DB_HOST SUPABASE_DB_PORT SUPABASE_DB_NAME SUPABASE_DB_USER SUPABASE_DB_PASSWORD; do
            if [[ -z "${!VAR:-}" ]]; then
                echo "Missing $VAR. Copy infra/supabase.env.example to infra/supabase.env and fill it in."
                exit 1
            fi
        done

        RESOLVED_DB_HOST="$SUPABASE_DB_HOST"
        RESOLVED_DB_PORT="$SUPABASE_DB_PORT"
        RESOLVED_DB_NAME="$SUPABASE_DB_NAME"
        RESOLVED_DB_USER="$SUPABASE_DB_USER"
        RESOLVED_DB_PASSWORD="$SUPABASE_DB_PASSWORD"

    elif [[ "$DB_PROVIDER" == "rds" ]]; then

        echo "DB_PROVIDER=rds — reading RDS outputs/secrets.env"

        if [[ -z "${RDS_PASSWORD:-}" ]]; then
            echo "Cannot resolve RDS connection values."
            echo "RDS_PASSWORD not found in secrets.env"
            exit 1
        fi

        RESOLVED_DB_HOST=$(require_output RDS_ENDPOINT)
        RESOLVED_DB_PORT="$DB_PORT"
        RESOLVED_DB_NAME="$DB_NAME"
        RESOLVED_DB_USER="$DB_USERNAME"
        RESOLVED_DB_PASSWORD="$RDS_PASSWORD"

    else
        echo "Unknown DB_PROVIDER: $DB_PROVIDER (expected 'supabase' or 'rds')"
        exit 1
    fi
}

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

    SECRET_VALUE=$(jq -n \
        --arg username "$RESOLVED_DB_USER" \
        --arg password "$RESOLVED_DB_PASSWORD" \
        --arg host "$RESOLVED_DB_HOST" \
        --arg port "$RESOLVED_DB_PORT" \
        --arg dbname "$RESOLVED_DB_NAME" \
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
            --description "Database credentials for ${PROJECT_NAME} (provider: ${DB_PROVIDER})" \
            --secret-string "$SECRET_VALUE" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
            --query "ARN" \
            --output text)

    else

        echo "Secret already exists. Updating value for provider=${DB_PROVIDER}..."

        aws secretsmanager put-secret-value \
            --region "$AWS_REGION" \
            --secret-id "$SECRET_NAME" \
            --secret-string "$SECRET_VALUE" \
            >/dev/null

        aws secretsmanager tag-resource \
            --region "$AWS_REGION" \
            --secret-id "$SECRET_NAME" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
                Key=DbProvider,Value="$DB_PROVIDER" \
            >/dev/null

    fi

    append_output "RDS_SECRET_ARN" "$SECRET_ARN"

}

remove_password_from_outputs() {

    grep -v "^RDS_PASSWORD=" "$OUTPUT_FILE" \
        > "${OUTPUT_FILE}.tmp" || true

    mv "${OUTPUT_FILE}.tmp" "$OUTPUT_FILE"

}

# --- Wompi payment credentials -------------------------------------------
WOMPI_SECRET_NAME="${PROJECT_NAME}/wompi"

create_or_update_wompi_secret() {

    if [[ ! -f "$SCRIPT_DIR/../wompi.env" ]]; then
        echo "infra/wompi.env not found - skipping Wompi secret."
        echo "Copy infra/wompi.env.example to infra/wompi.env and fill it in to enable this."
        return
    fi

    source "$SCRIPT_DIR/../wompi.env"

    for VAR in WOMPI_CLIENT_ID WOMPI_CLIENT_SECRET WOMPI_APP_ID WOMPI_API_SECRET; do
        if [[ -z "${!VAR:-}" ]]; then
            echo "Missing $VAR in infra/wompi.env."
            exit 1
        fi
    done

    local SECRET_ARN

    if aws secretsmanager describe-secret \
        --region "$AWS_REGION" \
        --secret-id "$WOMPI_SECRET_NAME" >/dev/null 2>&1; then

        SECRET_ARN=$(aws secretsmanager describe-secret \
            --region "$AWS_REGION" \
            --secret-id "$WOMPI_SECRET_NAME" \
            --query ARN \
            --output text)

    else
        SECRET_ARN=""
    fi

    SECRET_VALUE=$(jq -n \
        --arg clientId "$WOMPI_CLIENT_ID" \
        --arg clientSecret "$WOMPI_CLIENT_SECRET" \
        --arg appId "$WOMPI_APP_ID" \
        --arg apiSecret "$WOMPI_API_SECRET" \
    '{
        "WOMPI_CLIENT_ID": $clientId,
        "WOMPI_CLIENT_SECRET": $clientSecret,
        "WOMPI_APP_ID": $appId,
        "WOMPI_API_SECRET": $apiSecret
    }')

    if [[ -z "$SECRET_ARN" || "$SECRET_ARN" == "None" ]]; then

        echo "Creating Wompi secret..."

        SECRET_ARN=$(aws secretsmanager create-secret \
            --region "$AWS_REGION" \
            --name "$WOMPI_SECRET_NAME" \
            --description "Wompi payment gateway credentials for ${PROJECT_NAME}" \
            --secret-string "$SECRET_VALUE" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
            --query "ARN" \
            --output text)

    else

        echo "Wompi secret already exists. Updating value..."

        aws secretsmanager put-secret-value \
            --region "$AWS_REGION" \
            --secret-id "$WOMPI_SECRET_NAME" \
            --secret-string "$SECRET_VALUE" \
            >/dev/null

    fi

    append_output "WOMPI_SECRET_ARN" "$SECRET_ARN"

}

# --- Firebase Cloud Messaging credentials -------------------------------
FIREBASE_SECRET_NAME="${PROJECT_NAME}/firebase"

create_or_update_firebase_secret() {

    if [[ ! -f "$SCRIPT_DIR/../firebase.env" ]]; then
        echo "infra/firebase.env not found - creating a disabled placeholder."
        FIREBASE_PROJECT_ID=""
        FIREBASE_SERVICE_ACCOUNT_JSON_BASE64=""
    else
        source "$SCRIPT_DIR/../firebase.env"

        for VAR in FIREBASE_PROJECT_ID \
                   FIREBASE_SERVICE_ACCOUNT_JSON_BASE64; do
            if [[ -z "${!VAR:-}" ]]; then
                echo "Missing $VAR in infra/firebase.env."
                exit 1
            fi
        done

        if ! printf '%s' "$FIREBASE_SERVICE_ACCOUNT_JSON_BASE64" \
                | base64 --decode 2>/dev/null | jq empty >/dev/null; then
            echo "FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 is not valid JSON base64."
            exit 1
        fi
    fi

    local SECRET_ARN
    SECRET_ARN=$(aws secretsmanager describe-secret \
        --region "$AWS_REGION" \
        --secret-id "$FIREBASE_SECRET_NAME" \
        --query ARN \
        --output text 2>/dev/null || true)

    SECRET_VALUE=$(jq -n \
        --arg projectId "$FIREBASE_PROJECT_ID" \
        --arg serviceAccount "$FIREBASE_SERVICE_ACCOUNT_JSON_BASE64" \
        '{
            "FIREBASE_PROJECT_ID": $projectId,
            "FIREBASE_SERVICE_ACCOUNT_JSON_BASE64": $serviceAccount
        }')

    if [[ -z "$SECRET_ARN" || "$SECRET_ARN" == "None" ]]; then
        echo "Creating Firebase secret..."
        SECRET_ARN=$(aws secretsmanager create-secret \
            --region "$AWS_REGION" \
            --name "$FIREBASE_SECRET_NAME" \
            --description "Firebase Admin credentials for ${PROJECT_NAME}" \
            --secret-string "$SECRET_VALUE" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
            --query ARN \
            --output text)
    else
        echo "Firebase secret already exists. Updating value..."
        aws secretsmanager put-secret-value \
            --region "$AWS_REGION" \
            --secret-id "$FIREBASE_SECRET_NAME" \
            --secret-string "$SECRET_VALUE" \
            >/dev/null
    fi

    append_output "FIREBASE_SECRET_ARN" "$SECRET_ARN"
}

resolve_connection_values

create_or_update_secret

remove_password_from_outputs

create_or_update_wompi_secret
create_or_update_firebase_secret

echo "Secret ready (provider: ${DB_PROVIDER})."
