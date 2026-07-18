#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking VPC..."


VPC_ID="${VPC_ID:-}"


if [[ -z "$VPC_ID" || "$VPC_ID" == "None" ]]; then

    echo "VPC_ID not found."

    exit 0

fi


EXISTS=$(aws ec2 describe-vpcs \
    --region "$AWS_REGION" \
    --vpc-ids "$VPC_ID" \
    --query "Vpcs[0].VpcId" \
    --output text 2>/dev/null || true)


if [[ -z "$EXISTS" || "$EXISTS" == "None" ]]; then

    echo "VPC already deleted."

    exit 0

fi


echo "Deleting VPC: $VPC_ID"


aws ec2 delete-vpc \
    --region "$AWS_REGION" \
    --vpc-id "$VPC_ID"


echo "VPC deleted successfully."


echo "Cleaning VPC output..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^VPC_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "VPC cleanup completed."