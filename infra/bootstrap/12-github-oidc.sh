#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPT_DIR/../config.env"
source "$SCRIPT_DIR/../outputs.env"
source "$SCRIPT_DIR/../lib/helpers.sh"

OUTPUT_FILE="$SCRIPT_DIR/../outputs.env"

OIDC_PROVIDER_URL="token.actions.githubusercontent.com"

create_oidc_provider() {

    local PROVIDER_ARN

    PROVIDER_ARN=$(aws iam list-open-id-connect-providers \
        --query "OpenIDConnectProviderList[?contains(Arn, 'token.actions.githubusercontent.com')].Arn" \
        --output text)


    if [[ -n "$PROVIDER_ARN" && "$PROVIDER_ARN" != "None" ]]; then

        echo "GitHub OIDC provider already exists."

        append_output "GITHUB_OIDC_PROVIDER_ARN" "$PROVIDER_ARN"

        return

    fi

    echo "Creating GitHub OIDC provider..."

    PROVIDER_ARN=$(aws iam create-open-id-connect-provider \
        --url "https://${OIDC_PROVIDER_URL}" \
        --client-id-list "sts.amazonaws.com" \
        --thumbprint-list "6938fd4d98bab03faadb97b34396831e3780aea1" \
        --query "OpenIDConnectProviderArn" \
        --output text)

    append_output "GITHUB_OIDC_PROVIDER_ARN" "$PROVIDER_ARN"

}

create_deploy_role() {

    local ROLE_NAME="$GITHUB_ACTIONS_ROLE_NAME"

    local ROLE_ARN

    ROLE_ARN=$(aws iam get-role \
        --role-name "$ROLE_NAME" \
        --query "Role.Arn" \
        --output text 2>/dev/null || true)


    if [[ -n "$ROLE_ARN" && "$ROLE_ARN" != "None" ]]; then

        echo "GitHub Actions role already exists."

    else

        echo "Creating GitHub Actions role..."

        TRUST_POLICY=$(cat <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Federated": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):oidc-provider/${OIDC_PROVIDER_URL}"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringEquals": {
                    "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
                },
                "StringLike": {
                    "token.actions.githubusercontent.com:sub": "repo:${GITHUB_OWNER}/${GITHUB_REPOSITORY}:ref:refs/heads/${GITHUB_BRANCH}"
                }
            }
        }
    ]
}
EOF
)

        ROLE_ARN=$(aws iam create-role \
            --role-name "$ROLE_NAME" \
            --assume-role-policy-document "$TRUST_POLICY" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="bash" \
            --query "Role.Arn" \
            --output text)

    fi

    append_output "GITHUB_ACTIONS_ROLE_ARN" "$ROLE_ARN"

}

create_inline_policy() {

    local ROLE_NAME="$GITHUB_ACTIONS_ROLE_NAME"


    ECS_EXECUTION_ROLE_ARN=$(require_output ECS_TASK_EXECUTION_ROLE_ARN)

    ECS_TASK_ROLE_ARN=$(require_output ECS_TASK_ROLE_ARN)


    ACCOUNT_ID=$(aws sts get-caller-identity \
        --query Account \
        --output text)


    POLICY=$(cat <<EOF
{
    "Version": "2012-10-17",
    "Statement": [

        {
            "Effect": "Allow",
            "Action": [
                "ecr:GetAuthorizationToken"
            ],
            "Resource": "*"
        },

        {
            "Effect": "Allow",
            "Action": [
                "ecr:BatchCheckLayerAvailability",
                "ecr:CompleteLayerUpload",
                "ecr:GetDownloadUrlForLayer",
                "ecr:InitiateLayerUpload",
                "ecr:PutImage",
                "ecr:UploadLayerPart"
            ],
            "Resource": "arn:aws:ecr:${AWS_REGION}:${ACCOUNT_ID}:repository/${ECR_REPOSITORY}"
        },

        {
            "Effect": "Allow",
            "Action": [
                "ecs:RegisterTaskDefinition",
                "ecs:DescribeTaskDefinition",
                "ecs:DescribeServices",
                "ecs:UpdateService"
            ],
            "Resource": "*"
        },

        {
            "Effect": "Allow",
            "Action": [
                "iam:PassRole"
            ],
            "Resource": [
                "$ECS_EXECUTION_ROLE_ARN",
                "$ECS_TASK_ROLE_ARN"
            ]
        }

    ]
}
EOF
)
    aws iam put-role-policy \
        --role-name "$ROLE_NAME" \
        --policy-name "${PROJECT_NAME}-github-deploy-policy" \
        --policy-document "$POLICY"


}

create_oidc_provider

create_deploy_role

create_inline_policy

echo "GitHub OIDC configured."