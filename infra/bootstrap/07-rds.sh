#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

VPC_ID=$(require_output VPC_ID)
PRIVATE_SUBNET_A_ID=$(require_output PRIVATE_SUBNET_A_ID)
PRIVATE_SUBNET_B_ID=$(require_output PRIVATE_SUBNET_B_ID)
RDS_SECURITY_GROUP_ID=$(require_output RDS_SECURITY_GROUP_ID)
DB_SUBNET_GROUP_NAME=$(require_output DB_SUBNET_GROUP_NAME)

create_db_subnet_group() {

    local NAME="${PROJECT_NAME}-db-subnet-group"

    if aws rds describe-db-subnet-groups \
        --db-subnet-group-name "$NAME" \
        --region "$AWS_REGION" >/dev/null 2>&1; then

        echo "DB subnet group already exists."

    else

        echo "Creating DB subnet group..."

        aws rds create-db-subnet-group \
            --region "$AWS_REGION" \
            --db-subnet-group-name "$NAME" \
            --db-subnet-group-description "Subnet group for ${PROJECT_NAME}" \
            --subnet-ids \
                "$PRIVATE_SUBNET_A_ID" \
                "$PRIVATE_SUBNET_B_ID" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY"

    fi

    append_output "DB_SUBNET_GROUP_NAME" "$NAME"
}

generate_password() {

    openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 30

}

create_rds_instance() {

    local INSTANCE_ID="$RDS_INSTANCE_IDENTIFIER"

    if aws rds describe-db-instances \
        --db-instance-identifier "$INSTANCE_ID" \
        --region "$AWS_REGION" >/dev/null 2>&1; then

        echo "RDS instance already exists."

        return

    fi

    echo "Generating master password..."

    local PASSWORD

    PASSWORD=$(generate_password)

    echo "Creating RDS instance..."

    aws rds create-db-instance \
        --region "$AWS_REGION" \
        --db-instance-identifier "$INSTANCE_ID" \
        --db-instance-class "$RDS_INSTANCE_CLASS" \
        --engine "$DB_ENGINE" \
        --engine-version "$RDS_ENGINE_VERSION" \
        --allocated-storage "$RDS_STORAGE_GB" \
        --storage-type gp3 \
        --db-name "$DB_NAME" \
        --master-username "$DB_USERNAME" \
        --master-user-password "$PASSWORD" \
        --db-subnet-group-name "$DB_SUBNET_GROUP_NAME" \
        --vpc-security-groups "$RDS_SECURITY_GROUP_ID" \
        --backup-retention-period "$RDS_BACKUP_RETENTION_DAYS" \
        --no-publicly-accessible \
        --storage-encrypted \
        --no-multi-az \
        --deletion-protection false \
        --tags \
            Key=Project,Value="$PROJECT_NAME" \
            Key=Environment,Value="$ENVIRONMENT" \
            Key=ManagedBy,Value="$MANAGED_BY"

    append_output "RDS_PASSWORD" "$PASSWORD"

}

wait_for_rds() {

    echo "Waiting for RDS availability..."

    aws rds wait db-instance-available \
        --region "$AWS_REGION" \
        --db-instance-identifier "$RDS_INSTANCE_IDENTIFIER"

}

save_rds_information() {

    local ENDPOINT

    ENDPOINT=$(aws rds describe-db-instances \
        --region "$AWS_REGION" \
        --db-instance-identifier "$RDS_INSTANCE_IDENTIFIER" \
        --query "DBInstances[0].Endpoint.Address" \
        --output text)

    append_output "RDS_ENDPOINT" "$ENDPOINT"

    append_output "RDS_INSTANCE_ID" "$RDS_INSTANCE_IDENTIFIER"

}

create_db_subnet_group

create_rds_instance

wait_for_rds

save_rds_information

echo "RDS ready."