#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

echo "Checking Internet Gateway..."

IGW_ID=$(aws ec2 describe-internet-gateways \
    --region "$AWS_REGION" \
    --filters \
        "Name=tag:Name,Values=${PROJECT_NAME}-igw" \
    --query "InternetGateways[0].InternetGatewayId" \
    --output text)

if [[ "$IGW_ID" == "None" ]]; then

    echo "Creating Internet Gateway..."

    IGW_ID=$(aws ec2 create-internet-gateway \
        --region "$AWS_REGION" \
        --query "InternetGateway.InternetGatewayId" \
        --output text)

    aws ec2 create-tags \
        --region "$AWS_REGION" \
        --resources "$IGW_ID" \
        --tags \
            Key=Name,Value="${PROJECT_NAME}-igw" \
            Key=Project,Value="$PROJECT_NAME" \
            Key=Environment,Value="$ENVIRONMENT" \
            Key=ManagedBy,Value="bash"

    echo "Internet Gateway created."

else

    echo "Internet Gateway already exists."

fi

ATTACHED=$(aws ec2 describe-internet-gateways \
    --internet-gateway-ids "$IGW_ID" \
    --region "$AWS_REGION" \
    --query "InternetGateways[0].Attachments[?VpcId=='${VPC_ID}'] | length(@)" \
    --output text)

if [[ "$ATTACHED" == "0" ]]; then

    echo "Attaching Internet Gateway to VPC..."

    if [[ -z "${VPC_ID:-}" ]]; then
        echo "VPC_ID not found in outputs.env"
        exit 1
    fi

    aws ec2 attach-internet-gateway \
        --internet-gateway-id "$IGW_ID" \
        --vpc-id "$VPC_ID" \
        --region "$AWS_REGION"

    echo "Internet Gateway attached."

else

    echo "Internet Gateway already attached."

fi

append_output "INTERNET_GATEWAY_ID" "$IGW_ID"

echo "Internet Gateway ready: $IGW_ID"