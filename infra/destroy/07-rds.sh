#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking RDS instance..."

INSTANCE_ID="${RDS_INSTANCE_IDENTIFIER}"

RDS_EXISTS=$(aws rds describe-db-instances \
    --region "$AWS_REGION" \
    --db-instance-identifier "$INSTANCE_ID" \
    --query "DBInstances[0].DBInstanceIdentifier" \
    --output text 2>/dev/null || true)


if [[ -z "$RDS_EXISTS" || "$RDS_EXISTS" == "None" ]]; then

    echo "RDS instance already deleted."

else

    echo "Deleting RDS instance: $INSTANCE_ID"


    aws rds delete-db-instance \
        --region "$AWS_REGION" \
        --db-instance-identifier "$INSTANCE_ID" \
        --skip-final-snapshot \
        --delete-automated-backups


    echo "Waiting for RDS deletion..."

    aws rds wait db-instance-deleted \
        --region "$AWS_REGION" \
        --db-instance-identifier "$INSTANCE_ID"


    echo "RDS instance deleted successfully."

fi


DB_SUBNET_GROUP="${PROJECT_NAME}-db-subnet-group"


echo "Checking DB subnet group..."


SUBNET_GROUP_EXISTS=$(aws rds describe-db-subnet-groups \
    --region "$AWS_REGION" \
    --db-subnet-group-name "$DB_SUBNET_GROUP" \
    --query "DBSubnetGroups[0].DBSubnetGroupName" \
    --output text 2>/dev/null || true)


if [[ -z "$SUBNET_GROUP_EXISTS" || "$SUBNET_GROUP_EXISTS" == "None" ]]; then

    echo "DB subnet group already deleted."

else

    echo "Deleting DB subnet group..."

    aws rds delete-db-subnet-group \
        --region "$AWS_REGION" \
        --db-subnet-group-name "$DB_SUBNET_GROUP"


    echo "DB subnet group deleted successfully."

fi


echo "Cleaning local RDS outputs..."

if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^RDS_ENDPOINT=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^RDS_INSTANCE_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^DB_SUBNET_GROUP_NAME=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "RDS cleanup completed."