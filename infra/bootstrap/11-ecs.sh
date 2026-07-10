#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"


ECR_URI=$(require_output ECR_REPOSITORY_URI)
ECS_EXECUTION_ROLE=$(require_output ECS_TASK_EXECUTION_ROLE_ARN)
ECS_TASK_ROLE=$(require_output ECS_TASK_ROLE_ARN)
SECRET_ARN=$(require_output RDS_SECRET_ARN)
LOG_GROUP=$(require_output CLOUDWATCH_LOG_GROUP)
PUBLIC_SUBNET_A_ID=$(require_output PUBLIC_SUBNET_A_ID)
PUBLIC_SUBNET_B_ID=$(require_output PUBLIC_SUBNET_B_ID)
ECS_SECURITY_GROUP_ID=$(require_output ECS_SECURITY_GROUP_ID)


create_cluster() {

    if aws ecs describe-clusters \
        --cluster "$ECS_CLUSTER_NAME" \
        --region "$AWS_REGION" \
        --query "clusters[0].status" \
        --output text | grep -q ACTIVE; then

        echo "ECS cluster already exists."

    else

        echo "Creating ECS cluster..."

        aws ecs create-cluster \
            --cluster-name "$ECS_CLUSTER_NAME" \
            --region "$AWS_REGION" \
            --tags \
                key=Project,value="$PROJECT_NAME" \
                key=Environment,value="$ENVIRONMENT" \
                key=ManagedBy,value="bash"

    fi


    append_output "ECS_CLUSTER_NAME" "$ECS_CLUSTER_NAME"

}


create_task_definition() {


    echo "Creating task definition..."


    TASK_DEFINITION=$(cat <<EOF
{
  "family": "$ECS_TASK_FAMILY",
  "networkMode": "awsvpc",
  "requiresCompatibilities": [
    "FARGATE"
  ],
  "cpu": "$ECS_CPU",
  "memory": "$ECS_MEMORY",
  "executionRoleArn": "$ECS_EXECUTION_ROLE",
  "taskRoleArn": "$ECS_TASK_ROLE",

  "containerDefinitions": [
    {
      "name": "$ECS_CONTAINER_NAME",

      "image": "$ECR_URI:latest",

      "essential": true,

      "portMappings": [
        {
          "containerPort": $ECS_CONTAINER_PORT,
          "protocol": "tcp"
        }
      ],

      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },

        {
          "name": "AWS_REGION",
          "value": "$AWS_REGION"
        }
      ],

      "secrets": [
        {
          "name": "DB_HOST",
          "valueFrom": "${SECRET_ARN}:DB_HOST::"
        },
        {
          "name": "DB_PORT",
          "valueFrom": "${SECRET_ARN}:DB_PORT::"
        },
        {
          "name": "DB_NAME",
          "valueFrom": "${SECRET_ARN}:DB_NAME::"
        },
        {
          "name": "DB_USER",
          "valueFrom": "${SECRET_ARN}:DB_USER::"
        },
        {
          "name": "DB_PASSWORD",
          "valueFrom": "${SECRET_ARN}:DB_PASSWORD::"
        }
      ],

      "logConfiguration": {
        "logDriver": "awslogs",

        "options": {
          "awslogs-group": "$LOG_GROUP",
          "awslogs-region": "$AWS_REGION",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
EOF
)

    echo "$TASK_DEFINITION" > /tmp/bflow-task-definition.json

    aws ecs register-task-definition \
        --region "$AWS_REGION" \
        --cli-input-json file:///tmp/bflow-task-definition.json >/dev/null

    append_output "ECS_TASK_FAMILY" "$ECS_TASK_FAMILY"

}

create_service() {

    SERVICE_EXISTS=$(aws ecs describe-services \
        --cluster "$ECS_CLUSTER_NAME" \
        --services "$ECS_SERVICE_NAME" \
        --region "$AWS_REGION" \
        --query "services | length(@)" \
        --output text)

    if [[ "$SERVICE_EXISTS" == "0" ]]; then

        echo "Creating ECS service..."

        aws ecs create-service \
            --cluster "$ECS_CLUSTER_NAME" \
            --service-name "$ECS_SERVICE_NAME" \
            --task-definition "$ECS_TASK_FAMILY" \
            --desired-count "$ECS_DESIRED_COUNT" \
            --launch-type FARGATE \
            --platform-version LATEST \
            --deployment-configuration \
                "minimumHealthyPercent=$ECS_MIN_HEALTHY_PERCENT,maximumPercent=$ECS_MAX_PERCENT" \
            --network-configuration \
                "awsvpcConfiguration={
                    subnets=[$PUBLIC_SUBNET_A_ID,$PUBLIC_SUBNET_B_ID],
                    securityGroups=[$ECS_SECURITY_GROUP_ID],
                    assignPublicIp=ENABLED
                }" \
            --region "$AWS_REGION"

    else

        echo "ECS service already exists."

    fi

    append_output "ECS_SERVICE_NAME" "$ECS_SERVICE_NAME"

}

create_cluster

create_task_definition

create_service

echo "ECS infrastructure ready."