#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"


create_log_group() {

    local LOG_GROUP="$1"


    if aws logs describe-log-groups \
        --region "$AWS_REGION" \
        --log-group-name-prefix "$LOG_GROUP" \
        --query "logGroups[?logGroupName=='${LOG_GROUP}'] | length(@)" \
        --output text | grep -q "1"; then

        echo "Log group already exists."

    else

        echo "Creating log group..."

        aws logs create-log-group \
            --region "$AWS_REGION" \
            --log-group-name "$LOG_GROUP"


        aws logs tag-log-group \
            --region "$AWS_REGION" \
            --log-group-name "$LOG_GROUP" \
            --tags \
                Project="$PROJECT_NAME" \
                Environment="$ENVIRONMENT" \
                ManagedBy="bash"

    fi


    aws logs put-retention-policy \
        --region "$AWS_REGION" \
        --log-group-name "$LOG_GROUP" \
        --retention-in-days "$CLOUDWATCH_LOG_RETENTION_DAYS"


    append_output "CLOUDWATCH_LOG_GROUP" "$LOG_GROUP"

}


create_log_group "$CLOUDWATCH_LOG_GROUP"


echo "CloudWatch configured."