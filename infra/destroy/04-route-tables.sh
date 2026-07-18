#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

echo "Checking route tables..."

remove_association() {

    local ROUTE_TABLE_ID="$1"

    if [[ -z "$ROUTE_TABLE_ID" || "$ROUTE_TABLE_ID" == "None" ]]; then
        return
    fi


    ASSOCIATIONS=$(aws ec2 describe-route-tables \
        --region "$AWS_REGION" \
        --route-table-ids "$ROUTE_TABLE_ID" \
        --query "RouteTables[0].Associations[].RouteTableAssociationId" \
        --output text 2>/dev/null || true)


    if [[ -z "$ASSOCIATIONS" ]]; then
        return
    fi


    for ASSOCIATION_ID in $ASSOCIATIONS; do

        MAIN=$(aws ec2 describe-route-tables \
            --region "$AWS_REGION" \
            --route-table-ids "$ROUTE_TABLE_ID" \
            --query "RouteTables[0].Associations[?RouteTableAssociationId=='${ASSOCIATION_ID}'].Main" \
            --output text)


        if [[ "$MAIN" != "True" ]]; then

            echo "Removing association: $ASSOCIATION_ID"

            aws ec2 disassociate-route-table \
                --region "$AWS_REGION" \
                --association-id "$ASSOCIATION_ID" || true

        fi

    done

}


delete_route_table() {

    local ROUTE_TABLE_ID="$1"
    local NAME="$2"


    if [[ -z "$ROUTE_TABLE_ID" || "$ROUTE_TABLE_ID" == "None" ]]; then

        echo "Route table not found: $NAME"

        return

    fi


    EXISTS=$(aws ec2 describe-route-tables \
        --region "$AWS_REGION" \
        --route-table-ids "$ROUTE_TABLE_ID" \
        --query "RouteTables[0].RouteTableId" \
        --output text 2>/dev/null || true)


    if [[ -z "$EXISTS" || "$EXISTS" == "None" ]]; then

        echo "Route table already deleted: $NAME"

        return

    fi


    remove_association "$ROUTE_TABLE_ID"


    echo "Deleting route table: $NAME"


    aws ec2 delete-route-table \
        --region "$AWS_REGION" \
        --route-table-id "$ROUTE_TABLE_ID"


    echo "Deleted: $NAME"

}


delete_route_table \
    "${PUBLIC_ROUTE_TABLE_ID:-}" \
    "${PROJECT_NAME}-public-rt"


delete_route_table \
    "${PRIVATE_ROUTE_TABLE_A_ID:-}" \
    "${PROJECT_NAME}-private-a-rt"


delete_route_table \
    "${PRIVATE_ROUTE_TABLE_B_ID:-}" \
    "${PROJECT_NAME}-private-b-rt"



echo "Cleaning route table outputs..."


if [[ -f "$SCRIPT_DIR/../outputs.env" ]]; then

    sed -i \
        '/^PUBLIC_ROUTE_TABLE_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^PRIVATE_ROUTE_TABLE_A_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

    sed -i \
        '/^PRIVATE_ROUTE_TABLE_B_ID=/d' \
        "$SCRIPT_DIR/../outputs.env"

fi


echo "Route tables cleanup completed."