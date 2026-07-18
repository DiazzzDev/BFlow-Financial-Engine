#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

echo "Checking availability zones..."

AZ_A=$(aws ec2 describe-availability-zones \
    --region "$AWS_REGION" \
    --query "AvailabilityZones[0].ZoneName" \
    --output text)

AZ_B=$(aws ec2 describe-availability-zones \
    --region "$AWS_REGION" \
    --query "AvailabilityZones[1].ZoneName" \
    --output text)

echo "Using:"
echo " - $AZ_A"
echo " - $AZ_B"

create_subnet() {

    local NAME="$1"
    local OUTPUT_KEY="$2"
    local CIDR="$3"
    local AZ="$4"
    local PUBLIC="$5"

    SUBNET_ID=$(aws ec2 describe-subnets \
        --region "$AWS_REGION" \
        --filters \
            "Name=vpc-id,Values=$VPC_ID" \
            "Name=tag:Name,Values=$NAME" \
        --query "Subnets[0].SubnetId" \
        --output text)

    if [[ "$SUBNET_ID" != "None" ]]; then
        echo "$NAME already exists."
        append_output "$OUTPUT_KEY" "$SUBNET_ID"
        return
    fi

    echo "Creating $NAME..."

    SUBNET_ID=$(aws ec2 create-subnet \
        --region "$AWS_REGION" \
        --vpc-id "$VPC_ID" \
        --cidr-block "$CIDR" \
        --availability-zone "$AZ" \
        --query "Subnet.SubnetId" \
        --output text)

    aws ec2 create-tags \
        --region "$AWS_REGION" \
        --resources "$SUBNET_ID" \
        --tags \
            Key=Name,Value="$NAME" \
            Key=Project,Value="$PROJECT_NAME" \
            Key=Environment,Value="$ENVIRONMENT" \
            Key=ManagedBy,Value="bash"

    if [[ "$PUBLIC" == "true" ]]; then
        aws ec2 modify-subnet-attribute \
            --subnet-id "$SUBNET_ID" \
            --map-public-ip-on-launch \
            --region "$AWS_REGION"
    fi

    append_output "$OUTPUT_KEY" "$SUBNET_ID"

    echo "$NAME created."
}

create_subnet \
    "${PROJECT_NAME}-public-a" \
    "PUBLIC_SUBNET_A_ID" \
    "$PUBLIC_SUBNET_A_CIDR" \
    "$AZ_A" \
    true

create_subnet \
    "${PROJECT_NAME}-public-b" \
    "PUBLIC_SUBNET_B_ID" \
    "$PUBLIC_SUBNET_B_CIDR" \
    "$AZ_B" \
    true

create_subnet \
    "${PROJECT_NAME}-private-a" \
    "PRIVATE_SUBNET_A_ID" \
    "$PRIVATE_SUBNET_A_CIDR" \
    "$AZ_A" \
    false

create_subnet \
    "${PROJECT_NAME}-private-b" \
    "PRIVATE_SUBNET_B_ID" \
    "$PRIVATE_SUBNET_B_CIDR" \
    "$AZ_B" \
    false

echo "Subnets created successfully."