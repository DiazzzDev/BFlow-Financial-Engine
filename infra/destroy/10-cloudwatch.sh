#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

LOG_GROUP="${CLOUDWATCH_LOG_GROUP}"

echo "Checking CloudWatch Log Group..."

LOG_GROUP_EXISTS=$(aws logs describe-log-groups \
    --region "$AWS_REGION" \
    --log-group-name-prefix "$LOG_GROUP" \
    --query "logGroups[?logGroupName=='${LOG_GROUP}'] | length(@)" \
    --output text)


if [[ "$LOG_GROUP_EXISTS" == "0" ]]; then

    echo "CloudWatch Log Group already deleted."

    exit 0

fi


echo "Deleting CloudWatch Log Group..."

aws logs delete-log-group \
    --region "$AWS_REGION" \
    --log-group-name "$LOG_GROUP"


echo "CloudWatch Log Group deleted successfully."