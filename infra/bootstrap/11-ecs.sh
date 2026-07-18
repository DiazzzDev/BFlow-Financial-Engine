#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

create_cluster() {

    STATUS=$(aws ecs describe-clusters \
        --clusters "$ECS_CLUSTER_NAME" \
        --region "$AWS_REGION" \
        --query "clusters[0].status" \
        --output text 2>/dev/null || echo "MISSING")

    if [[ "$STATUS" == "ACTIVE" ]]; then
        echo "ECS cluster already exists."

    elif [[ "$STATUS" == "INACTIVE" || "$STATUS" == "MISSING" || "$STATUS" == "None" ]]; then
        echo "Creating ECS cluster..."

        aws ecs create-cluster \
            --cluster-name "$ECS_CLUSTER_NAME" \
            --region "$AWS_REGION" \
            --tags \
                key=Project,value="$PROJECT_NAME" \
                key=Environment,value="$ENVIRONMENT" \
                key=ManagedBy,value="$MANAGED_BY" \
            >/dev/null

    else
        echo "Unexpected cluster status: $STATUS"
        exit 1
    fi

    CLUSTER_ARN=$(aws ecs describe-clusters \
        --clusters "$ECS_CLUSTER_NAME" \
        --region "$AWS_REGION" \
        --query "clusters[0].clusterArn" \
        --output text)

    append_output "ECS_CLUSTER_NAME" "$ECS_CLUSTER_NAME"
    append_output "ECS_CLUSTER_ARN" "$CLUSTER_ARN"
}

create_cluster

echo "ECS cluster ready."
echo "Task definition and ECS service will be managed by GitHub Actions."