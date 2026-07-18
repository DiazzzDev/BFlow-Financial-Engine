#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

VPC_ID=$(require_output VPC_ID)

command -v curl >/dev/null || {
    echo "curl is required."
    exit 1
}

create_security_group() {

    local NAME="$1"
    local OUTPUT_KEY="$2"
    local DESCRIPTION="$3"

    local SG_ID

    SG_ID=$(aws ec2 describe-security-groups \
        --region "$AWS_REGION" \
        --filters \
            "Name=vpc-id,Values=$VPC_ID" \
            "Name=tag:Name,Values=$NAME" \
        --query "SecurityGroups[0].GroupId" \
        --output text)

    if [[ "$SG_ID" == "None" ]]; then

        echo "Creating $NAME..."

        SG_ID=$(aws ec2 create-security-group \
            --region "$AWS_REGION" \
            --group-name "$NAME" \
            --description "$DESCRIPTION" \
            --vpc-id "$VPC_ID" \
            --query "GroupId" \
            --output text)

        aws ec2 create-tags \
            --region "$AWS_REGION" \
            --resources "$SG_ID" \
            --tags \
                Key=Name,Value="$NAME" \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY"

        echo "$NAME created."

    else

        echo "$NAME already exists."

    fi

    append_output "$OUTPUT_KEY" "$SG_ID"
}

create_security_group \
    "${PROJECT_NAME}-ecs-sg" \
    "ECS_SECURITY_GROUP_ID" \
    "Security group for ECS tasks"

create_security_group \
    "${PROJECT_NAME}-rds-sg" \
    "RDS_SECURITY_GROUP_ID" \
    "Security group for PostgreSQL"

ECS_SECURITY_GROUP_ID=$(require_output ECS_SECURITY_GROUP_ID)
RDS_SECURITY_GROUP_ID=$(require_output RDS_SECURITY_GROUP_ID)

authorize_cloudflare_ranges() {

    local SECURITY_GROUP_ID="$1"
    local PORT="$2"
    local URL="$3"
    local IP_TYPE="$4"

    echo "Downloading Cloudflare ${IP_TYPE} ranges..."

    local CIDRS

    CIDRS=$(curl -fsSL "$URL")

    [[ -z "$CIDRS" ]] && {
        echo "Unable to download Cloudflare ranges."
        exit 1
    }

    while read -r CIDR
    do

        [[ -z "$CIDR" ]] && continue

        if [[ "$IP_TYPE" == "ipv4" ]]; then

            EXISTS=$(aws ec2 describe-security-groups \
                --region "$AWS_REGION" \
                --group-ids "$ECS_SECURITY_GROUP_ID" \
                --query "SecurityGroups[0].IpPermissions[?FromPort==\`${PORT}\` && IpRanges[?CidrIp=='${CIDR}']]" \
                --output text)

            if [[ -z "$EXISTS" ]]; then

                echo "Authorizing $CIDR"

                aws ec2 authorize-security-group-ingress \
                    --region "$AWS_REGION" \
                    --group-id "$SECURITY_GROUP_ID" \
                    --protocol tcp \
                    --port "$PORT" \
                    --cidr "$CIDR"

            fi

        else

            EXISTS=$(aws ec2 describe-security-groups \
                --region "$AWS_REGION" \
                --group-ids "$ECS_SECURITY_GROUP_ID" \
                --query "SecurityGroups[0].IpPermissions[?FromPort==\`${PORT}\` && Ipv6Ranges[?CidrIpv6=='${CIDR}']]" \
                --output text)

            if [[ -z "$EXISTS" ]]; then

                echo "Authorizing $CIDR"

                aws ec2 authorize-security-group-ingress \
                    --region "$AWS_REGION" \
                    --group-id "$ECS_SECURITY_GROUP_ID" \
                    --protocol tcp \
                    --port "$PORT" \
                    --ipv6-cidr-block "$CIDR"

            fi

        fi

    done <<<"$CIDRS"

}

authorize_cloudflare_ranges \
    "$ECS_SECURITY_GROUP_ID" \
    "$APP_PORT" \
    "$CLOUDFLARE_IPV4_URL" \
    ipv4

#authorize_cloudflare_ranges \
#    "$ECS_SECURITY_GROUP_ID" \
#    "$APP_PORT" \
#    "$CLOUDFLARE_IPV6_URL" \
#    ipv6

echo "Checking RDS ingress rule..."

RDS_RULE=$(aws ec2 describe-security-groups \
    --region "$AWS_REGION" \
    --group-ids "$RDS_SECURITY_GROUP_ID" \
    --query "SecurityGroups[0].IpPermissions[?FromPort==\`5432\` && ToPort==\`5432\`]" \
    --output text)

if [[ -z "$RDS_RULE" ]]; then

    aws ec2 authorize-security-group-ingress \
        --region "$AWS_REGION" \
        --group-id "$RDS_SECURITY_GROUP_ID" \
        --protocol tcp \
        --port "$DB_PORT" \
        --source-group "$ECS_SECURITY_GROUP_ID"

fi

echo "Security groups configured successfully."