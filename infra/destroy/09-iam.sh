#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking IAM roles..."

delete_role() {

    local ROLE_NAME="$1"

    ROLE_EXISTS=$(aws iam get-role \
        --role-name "$ROLE_NAME" \
        --query "Role.RoleName" \
        --output text 2>/dev/null || true)


    if [[ -z "$ROLE_EXISTS" || "$ROLE_EXISTS" == "None" ]]; then

        echo "Role already deleted: $ROLE_NAME"

        return

    fi


    echo "Deleting inline policies from $ROLE_NAME..."


    INLINE_POLICIES=$(aws iam list-role-policies \
        --role-name "$ROLE_NAME" \
        --query "PolicyNames[]" \
        --output text)


    if [[ -n "$INLINE_POLICIES" ]]; then

        while read -r POLICY_NAME; do

            if [[ -n "$POLICY_NAME" ]]; then

                echo "Deleting inline policy: $POLICY_NAME"

                aws iam delete-role-policy \
                    --role-name "$ROLE_NAME" \
                    --policy-name "$POLICY_NAME"

            fi

        done <<< "$INLINE_POLICIES"

    fi


    echo "Detaching managed policies from $ROLE_NAME..."


    MANAGED_POLICIES=$(aws iam list-attached-role-policies \
        --role-name "$ROLE_NAME" \
        --query "AttachedPolicies[].PolicyArn" \
        --output text)


    if [[ -n "$MANAGED_POLICIES" ]]; then

        while read -r POLICY_ARN; do

            if [[ -n "$POLICY_ARN" ]]; then

                echo "Detaching policy: $POLICY_ARN"

                aws iam detach-role-policy \
                    --role-name "$ROLE_NAME" \
                    --policy-arn "$POLICY_ARN"

            fi

        done <<< "$MANAGED_POLICIES"

    fi


    echo "Deleting role: $ROLE_NAME"


    aws iam delete-role \
        --role-name "$ROLE_NAME"


    echo "Role deleted: $ROLE_NAME"

}


delete_role "$ECS_TASK_EXECUTION_ROLE_NAME"

delete_role "$ECS_TASK_ROLE_NAME"


echo "IAM cleanup completed."