#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking subnets..."


delete_subnet() {

    local SUBNET_ID="$1"
    local NAME="$2"


    if [[ -z "$SUBNET_ID" || "$SUBNET_ID" == "None" ]]; then

        echo "Subnet not found: $NAME"

        return

    fi


    EXISTS=$(aws ec2 describe-subnets \
        --region "$AWS_REGION" \
        --subnet-ids "$SUBNET_ID" \
        --query "Subnets[0].SubnetId" \
        --output text 2>/dev/null || true)


    if [[ -z "$EXISTS" || "$EXISTS" == "None" ]]; then

        echo "Subnet already deleted: $NAME"

        return

    fi


    echo "Deleting subnet: $NAME ($SUBNET_ID)"


    aws ec2 delete-subnet \
        --region "$AWS_REGION" \
        --subnet-id "$SUBNET_ID"


    echo "Subnet deleted: $NAME"

}


delete_subnet \
    "${PUBLIC_SUBNET_A_ID:-}" \
    "${PROJECT_NAME}-public-a"


delete_subnet \
    "${PUBLIC_SUBNET_B_ID:-}" \
    "${PROJECT_NAME}-public-b"


delete_subnet \
    "${PRIVATE_SUBNET_A_ID:-}" \
    "${PROJECT_NAME}-private-a"


delete_subnet \
    "${PRIVATE_SUBNET_B_ID:-}" \
    "${PROJECT_NAME}-private-b"



echo "Cleaning subnet outputs..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^PUBLIC_SUBNET_A_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^PUBLIC_SUBNET_B_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^PRIVATE_SUBNET_A_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^PRIVATE_SUBNET_B_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "Subnets cleanup completed."