#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

echo "Checking ECR repository..."

read -r REPOSITORY_URI REPOSITORY_ARN <<<"$(
aws ecr describe-repositories \
    --region "$AWS_REGION" \
    --repository-names "$ECR_REPOSITORY" \
    --query "repositories[0].[repositoryUri,repositoryArn]" \
    --output text 2>/dev/null || true
)"

if [[ "$REPOSITORY_URI" == "None" || -z "$REPOSITORY_URI" ]]; then

    echo "Creating ECR repository..."

    read -r REPOSITORY_URI REPOSITORY_ARN <<<"$(
    aws ecr create-repository \
        --region "$AWS_REGION" \
        --repository-name "$ECR_REPOSITORY" \
        --image-scanning-configuration scanOnPush=true \
        --image-tag-mutability IMMUTABLE \
        --encryption-configuration encryptionType=AES256 \
        --query "repository.[repositoryUri,repositoryArn]" \
        --output text
    )"

    echo "ECR repository created."

else

    echo "ECR repository already exists."

fi

echo "Ensuring repository configuration..."

aws ecr put-image-tag-mutability \
    --region "$AWS_REGION" \
    --repository-name "$ECR_REPOSITORY" \
    --image-tag-mutability IMMUTABLE

aws ecr put-image-scanning-configuration \
    --region "$AWS_REGION" \
    --repository-name "$ECR_REPOSITORY" \
    --image-scanning-configuration scanOnPush=true

aws ecr tag-resource \
    --region "$AWS_REGION" \
    --resource-arn "$REPOSITORY_ARN" \
    --tags \
        Key=Project,Value="$PROJECT_NAME" \
        Key=Environment,Value="$ENVIRONMENT" \
        Key=ManagedBy,Value="$MANAGED_BY"


append_output "ECR_REPOSITORY_URI" "$REPOSITORY_URI"
append_output "ECR_REPOSITORY_ARN" "$REPOSITORY_ARN"

echo "ECR ready:"
echo "$REPOSITORY_URI"