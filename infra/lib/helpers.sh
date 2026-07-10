append_output() {
    local KEY="$1"
    local VALUE="$2"

    grep -v "^${KEY}=" "$OUTPUT_FILE" > "${OUTPUT_FILE}.tmp" || true

    mv "${OUTPUT_FILE}.tmp" "$OUTPUT_FILE"

    echo "${KEY}=${VALUE}" >> "$OUTPUT_FILE"
}

get_output() {
    local KEY="$1"

    grep "^${KEY}=" "$OUTPUT_FILE" | cut -d= -f2-
}

require_output() {
    local KEY="$1"
    local VALUE

    VALUE=$(get_output "$KEY")

    if [[ -z "$VALUE" ]]; then
        echo "Missing required output: $KEY"
        exit 1
    fi

    echo "$VALUE"
}

resource_exists() {
    local VALUE="$1"

    [[ -n "$VALUE" && "$VALUE" != "None" ]]
}

ensure_tag() {
    local RESOURCE_ID="$1"

    aws ec2 create-tags \
        --region "$AWS_REGION" \
        --resources "$RESOURCE_ID" \
        --tags \
            Key=Project,Value="$PROJECT_NAME" \
            Key=Environment,Value="$ENVIRONMENT" \
            Key=ManagedBy,Value="bash"
}