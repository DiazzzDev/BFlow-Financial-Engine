#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

INTERNET_GATEWAY_ID=$(require_output INTERNET_GATEWAY_ID)
VPC_ID=$(require_output VPC_ID)
INTERNET_GATEWAY_ID=$(require_output INTERNET_GATEWAY_ID)

create_route_table() {

    local NAME="$1"
    local OUTPUT_KEY="$2"

    local RT_ID

    RT_ID=$(aws ec2 describe-route-tables \
        --region "$AWS_REGION" \
        --filters \
            "Name=vpc-id,Values=$VPC_ID" \
            "Name=tag:Name,Values=$NAME" \
        --query "RouteTables[0].RouteTableId" \
        --output text)

    if [[ "$RT_ID" == "None" ]]; then

        echo "Creating $NAME..."

        RT_ID=$(aws ec2 create-route-table \
            --region "$AWS_REGION" \
            --vpc-id "$VPC_ID" \
            --query "RouteTable.RouteTableId" \
            --output text)

        aws ec2 create-tags \
            --region "$AWS_REGION" \
            --resources "$RT_ID" \
            --tags \
                Key=Name,Value="$NAME" \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="bash"

        echo "$NAME created."

    else

        echo "$NAME already exists."

    fi

    append_output "$OUTPUT_KEY" "$RT_ID"
}

# 1. Create Route Tables
create_route_table \
    "${PROJECT_NAME}-public-rt" \
    "PUBLIC_ROUTE_TABLE_ID"

create_route_table \
    "${PROJECT_NAME}-private-a-rt" \
    "PRIVATE_ROUTE_TABLE_A_ID"

create_route_table \
    "${PROJECT_NAME}-private-b-rt" \
    "PRIVATE_ROUTE_TABLE_B_ID"

PUBLIC_ROUTE_TABLE_ID=$(get_output PUBLIC_ROUTE_TABLE_ID)
PRIVATE_ROUTE_TABLE_A_ID=$(get_output PRIVATE_ROUTE_TABLE_A_ID)
PRIVATE_ROUTE_TABLE_B_ID=$(get_output PRIVATE_ROUTE_TABLE_B_ID)

echo "Checking public route..."

PUBLIC_ROUTE=$(aws ec2 describe-route-tables \
    --region "$AWS_REGION" \
    --route-table-ids "$PUBLIC_ROUTE_TABLE_ID" \
    --query "RouteTables[0].Routes[?DestinationCidrBlock=='0.0.0.0/0'] | length(@)" \
    --output text)

if [[ "$PUBLIC_ROUTE" == "0" ]]; then

    aws ec2 create-route \
        --region "$AWS_REGION" \
        --route-table-id "$PUBLIC_ROUTE_TABLE_ID" \
        --destination-cidr-block 0.0.0.0/0 \
        --gateway-id "$INTERNET_GATEWAY_ID"

fi

# 2. Association Helper Function
associate_route_table() {

    local SUBNET_ID="$1"
    local ROUTE_TABLE_ID="$2"

    local ASSOCIATED

    ASSOCIATED=$(aws ec2 describe-route-tables \
        --region "$AWS_REGION" \
        --route-table-ids "$ROUTE_TABLE_ID" \
        --query "RouteTables[0].Associations[?SubnetId=='${SUBNET_ID}'] | length(@)" \
        --output text)

    if [[ "$ASSOCIATED" == "0" ]]; then

        aws ec2 associate-route-table \
            --region "$AWS_REGION" \
            --subnet-id "$SUBNET_ID" \
            --route-table-id "$ROUTE_TABLE_ID" >/dev/null

    fi
}

# 3. Extract Subnet IDs from outputs
PUBLIC_SUBNET_A_ID=$(get_output PUBLIC_SUBNET_A_ID)
PUBLIC_SUBNET_B_ID=$(get_output PUBLIC_SUBNET_B_ID)

PRIVATE_SUBNET_A_ID=$(get_output PRIVATE_SUBNET_A_ID)
PRIVATE_SUBNET_B_ID=$(get_output PRIVATE_SUBNET_B_ID)

# 4. Execute Subnet to Route Table Associations
associate_route_table "$PUBLIC_SUBNET_A_ID" "$PUBLIC_ROUTE_TABLE_ID"
associate_route_table "$PUBLIC_SUBNET_B_ID" "$PUBLIC_ROUTE_TABLE_ID"

associate_route_table "$PRIVATE_SUBNET_A_ID" "$PRIVATE_ROUTE_TABLE_A_ID"
associate_route_table "$PRIVATE_SUBNET_B_ID" "$PRIVATE_ROUTE_TABLE_B_ID"

echo "Route tables configured successfully."