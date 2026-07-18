#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

echo "Checking VPC..."

VPC_ID=$(aws ec2 describe-vpcs \
    --filters "Name=tag:Project,Values=$PROJECT_NAME" \
              "Name=tag:Environment,Values=$ENVIRONMENT" \
    --query "Vpcs[0].VpcId" \
    --output text \
    --region "$AWS_REGION")

if [[ -n "$VPC_ID" && "$VPC_ID" != "None" ]]; then
    echo "VPC already exists: $VPC_ID"
    exit 0
fi

echo "Creating VPC..."

VPC_ID=$(aws ec2 create-vpc \
    --cidr-block "$VPC_CIDR" \
    --query "Vpc.VpcId" \
    --output text \
    --region "$AWS_REGION")

aws ec2 create-tags \
    --resources "$VPC_ID" \
    --tags \
        Key=Name,Value="$PROJECT_NAME-vpc" \
        Key=Project,Value="$PROJECT_NAME" \
        Key=ManagedBy,Value="bash" \
        Key=Environment,Value="$ENVIRONMENT"

aws ec2 modify-vpc-attribute \
    --vpc-id "$VPC_ID" \
    --enable-dns-support \
    --region "$AWS_REGION"

aws ec2 modify-vpc-attribute \
    --vpc-id "$VPC_ID" \
    --enable-dns-hostnames \
    --region "$AWS_REGION"

echo "VPC created successfully."

# Reemplazar el cat por append_output
append_output "VPC_ID" "$VPC_ID"

echo "Saved outputs to outputs.env"