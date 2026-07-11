#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking security groups..."

delete_security_group() {

    local GROUP_ID="$1"
    local GROUP_NAME="$2"


    if [[ -z "$GROUP_ID" || "$GROUP_ID" == "None" ]]; then

        echo "Security group not found: $GROUP_NAME"

        return

    fi


    EXISTS=$(aws ec2 describe-security-groups \
        --region "$AWS_REGION" \
        --group-ids "$GROUP_ID" \
        --query "SecurityGroups[0].GroupId" \
        --output text 2>/dev/null || true)


    if [[ -z "$EXISTS" || "$EXISTS" == "None" ]]; then

        echo "Security group already deleted: $GROUP_NAME"

        return

    fi


    echo "Deleting security group: $GROUP_NAME"


    aws ec2 delete-security-group \
        --region "$AWS_REGION" \
        --group-id "$GROUP_ID"


    echo "Deleted: $GROUP_NAME"

}

delete_security_group \
    "${RDS_SECURITY_GROUP_ID:-}" \
    "${PROJECT_NAME}-rds-sg"


delete_security_group \
    "${ECS_SECURITY_GROUP_ID:-}" \
    "${PROJECT_NAME}-ecs-sg"


echo "Cleaning security group outputs..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^RDS_SECURITY_GROUP_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^ECS_SECURITY_GROUP_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "Security groups cleanup completed."