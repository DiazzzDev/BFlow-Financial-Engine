#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking ECR repository: $ECR_REPOSITORY"


REPOSITORY_EXISTS=$(aws ecr describe-repositories \
    --region "$AWS_REGION" \
    --repository-names "$ECR_REPOSITORY" \
    --query "repositories[0].repositoryName" \
    --output text 2>/dev/null || true)


if [[ -z "$REPOSITORY_EXISTS" || "$REPOSITORY_EXISTS" == "None" ]]; then

    echo "ECR repository already deleted."

    exit 0

fi


echo "Deleting ECR images..."


IMAGE_IDS=$(aws ecr list-images \
    --region "$AWS_REGION" \
    --repository-name "$ECR_REPOSITORY" \
    --query "imageIds[]" \
    --output json)


if [[ "$IMAGE_IDS" != "[]" ]]; then

    aws ecr batch-delete-image \
        --region "$AWS_REGION" \
        --repository-name "$ECR_REPOSITORY" \
        --image-ids "$IMAGE_IDS" >/dev/null || true

    echo "Images deleted."

else

    echo "No images found."

fi


echo "Deleting ECR repository..."


aws ecr delete-repository \
    --region "$AWS_REGION" \
    --repository-name "$ECR_REPOSITORY" \
    --force


echo "ECR repository deleted successfully."


echo "Cleaning local outputs..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^ECR_REPOSITORY_URI=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^ECR_REPOSITORY_ARN=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "ECR cleanup completed."