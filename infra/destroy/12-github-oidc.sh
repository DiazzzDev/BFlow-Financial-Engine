#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking GitHub OIDC resources..."

ROLE_NAME="${GITHUB_OIDC_ROLE_NAME:-${PROJECT_NAME}-github-actions-role}"

ROLE_ARN=$(aws iam get-role \
    --role-name "$ROLE_NAME" \
    --query "Role.Arn" \
    --output text 2>/dev/null || true)


if [[ -z "$ROLE_ARN" || "$ROLE_ARN" == "None" ]]; then

    echo "GitHub OIDC IAM role already deleted."

    exit 0

fi


echo "Removing attached policies..."

ATTACHED_POLICIES=$(aws iam list-attached-role-policies \
    --role-name "$ROLE_NAME" \
    --query "AttachedPolicies[].PolicyArn" \
    --output text)


if [[ -n "$ATTACHED_POLICIES" ]]; then

    while read -r POLICY_ARN; do

        if [[ -n "$POLICY_ARN" ]]; then

            echo "Detaching policy: $POLICY_ARN"

            aws iam detach-role-policy \
                --role-name "$ROLE_NAME" \
                --policy-arn "$POLICY_ARN"

        fi

    done <<< "$ATTACHED_POLICIES"

fi


echo "Removing inline policies..."

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


echo "Deleting GitHub OIDC role..."

aws iam delete-role \
    --role-name "$ROLE_NAME"


echo "GitHub OIDC role deleted successfully."