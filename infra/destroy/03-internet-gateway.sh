#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking Internet Gateway..."


IGW_ID="${INTERNET_GATEWAY_ID:-}"


if [[ -z "$IGW_ID" || "$IGW_ID" == "None" ]]; then

    echo "Internet Gateway ID not found."

    exit 0

fi


EXISTS=$(aws ec2 describe-internet-gateways \
    --region "$AWS_REGION" \
    --internet-gateway-ids "$IGW_ID" \
    --query "InternetGateways[0].InternetGatewayId" \
    --output text 2>/dev/null || true)


if [[ -z "$EXISTS" || "$EXISTS" == "None" ]]; then

    echo "Internet Gateway already deleted."

    exit 0

fi


ATTACHED=$(aws ec2 describe-internet-gateways \
    --region "$AWS_REGION" \
    --internet-gateway-ids "$IGW_ID" \
    --query "InternetGateways[0].Attachments[0].VpcId" \
    --output text)


if [[ -n "$ATTACHED" && "$ATTACHED" != "None" ]]; then

    echo "Detaching Internet Gateway from VPC..."


    aws ec2 detach-internet-gateway \
        --region "$AWS_REGION" \
        --internet-gateway-id "$IGW_ID" \
        --vpc-id "$ATTACHED"


    echo "Internet Gateway detached."

fi


echo "Deleting Internet Gateway..."


aws ec2 delete-internet-gateway \
    --region "$AWS_REGION" \
    --internet-gateway-id "$IGW_ID"


echo "Internet Gateway deleted successfully."


echo "Cleaning output..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^INTERNET_GATEWAY_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "Internet Gateway cleanup completed."