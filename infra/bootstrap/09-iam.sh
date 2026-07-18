#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

create_role() {

    local ROLE_NAME="$1"
    local POLICY_DOCUMENT="$2"
    local OUTPUT_KEY="$3"

    local ROLE_ARN

    ROLE_ARN=$(aws iam get-role \
        --role-name "$ROLE_NAME" \
        --query "Role.Arn" \
        --output text 2>/dev/null || true)


    if [[ -z "$ROLE_ARN" || "$ROLE_ARN" == "None" ]]; then

        echo "Creating role: $ROLE_NAME"


        ROLE_ARN=$(aws iam create-role \
            --role-name "$ROLE_NAME" \
            --assume-role-policy-document "$POLICY_DOCUMENT" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="bash" \
            --query "Role.Arn" \
            --output text)

    else

        echo "Role already exists: $ROLE_NAME"

    fi

    append_output "$OUTPUT_KEY" "$ROLE_ARN"

}

attach_managed_policy() {

    local ROLE_NAME="$1"
    local POLICY_ARN="$2"


    ATTACHED=$(aws iam list-attached-role-policies \
        --role-name "$ROLE_NAME" \
        --query "AttachedPolicies[?PolicyArn=='${POLICY_ARN}'] | length(@)" \
        --output text)


    if [[ "$ATTACHED" == "0" ]]; then

        aws iam attach-role-policy \
            --role-name "$ROLE_NAME" \
            --policy-arn "$POLICY_ARN"

    fi

}

echo "Creating ECS execution role..."

EXECUTION_POLICY='{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ecs-tasks.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}'

create_role \
    "$ECS_TASK_EXECUTION_ROLE_NAME" \
    "$EXECUTION_POLICY" \
    "ECS_TASK_EXECUTION_ROLE_ARN"

attach_managed_policy \
    "$ECS_TASK_EXECUTION_ROLE_NAME" \
    "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"

echo "Creating ECS task role..."

TASK_POLICY='{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ecs-tasks.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}'

create_role \
    "$ECS_TASK_ROLE_NAME" \
    "$TASK_POLICY" \
    "ECS_TASK_ROLE_ARN"



echo "Creating inline task permissions..."

SECRET_ARN=$(require_output RDS_SECRET_ARN)

aws iam put-role-policy \
    --role-name "$ECS_TASK_ROLE_NAME" \
    --policy-name "${PROJECT_NAME}-secrets-access" \
    --policy-document "{
        \"Version\": \"2012-10-17\",
        \"Statement\": [
            {
                \"Effect\": \"Allow\",
                \"Action\": [
                    \"secretsmanager:GetSecretValue\"
                ],
                \"Resource\": [
                    \"$SECRET_ARN\"
                ]
            }
        ]
    }"

echo "IAM roles ready."