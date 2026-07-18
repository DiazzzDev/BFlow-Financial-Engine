#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking ECS cluster..."

CLUSTER_EXISTS=$(aws ecs describe-clusters \
    --cluster "$ECS_CLUSTER_NAME" \
    --region "$AWS_REGION" \
    --query "clusters[0].status" \
    --output text 2>/dev/null || true)


if [[ "$CLUSTER_EXISTS" == "None" || -z "$CLUSTER_EXISTS" ]]; then

    echo "ECS cluster already deleted."

    exit 0

fi


echo "Checking ECS services..."

SERVICES=$(aws ecs list-services \
    --cluster "$ECS_CLUSTER_NAME" \
    --region "$AWS_REGION" \
    --query "serviceArns[]" \
    --output text | tr '\t' '\n')


if [[ -n "$SERVICES" ]]; then

    while read -r SERVICE_ARN; do

        if [[ -n "$SERVICE_ARN" ]]; then

            SERVICE_NAME=$(basename "$SERVICE_ARN")

            echo "Scaling down service: $SERVICE_NAME"

            aws ecs update-service \
                --cluster "$ECS_CLUSTER_NAME" \
                --service "$SERVICE_NAME" \
                --desired-count 0 \
                --region "$AWS_REGION" >/dev/null


            echo "Waiting service stabilization..."

            aws ecs wait services-stable \
                --cluster "$ECS_CLUSTER_NAME" \
                --services "$SERVICE_NAME" \
                --region "$AWS_REGION" || true


            echo "Deleting service: $SERVICE_NAME"

            aws ecs delete-service \
                --cluster "$ECS_CLUSTER_NAME" \
                --service "$SERVICE_NAME" \
                --force \
                --region "$AWS_REGION"

        fi

    done <<< "$SERVICES"

fi


echo "Checking ECS task definitions..."

TASK_FAMILY="${ECS_TASK_FAMILY:-$PROJECT_NAME}"


TASK_DEFINITIONS=$(aws ecs list-task-definitions \
    --family-prefix "$TASK_FAMILY" \
    --region "$AWS_REGION" \
    --query "taskDefinitionArns[]" \
    --output text | tr '\t' '\n')


if [[ -n "$TASK_DEFINITIONS" ]]; then

    while read -r TASK_ARN; do

        if [[ -n "$TASK_ARN" ]]; then

            echo "Deregistering task definition..."

            aws ecs deregister-task-definition \
                --task-definition "$TASK_ARN" \
                --region "$AWS_REGION" >/dev/null

        fi

    done <<< "$TASK_DEFINITIONS"

fi


echo "Deleting ECS cluster..."

aws ecs delete-cluster \
    --cluster "$ECS_CLUSTER_NAME" \
    --region "$AWS_REGION" >/dev/null


echo "ECS cluster deleted successfully."