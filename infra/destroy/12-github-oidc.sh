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

    # Jobs that declare "environment: production" get an OIDC token whose sub
    # claim is "repo:OWNER/REPO:environment:production", NOT
    # "repo:OWNER/REPO:ref:refs/heads/BRANCH". Confirmed working in production
    # with StringEquals on this single value - kept exactly as verified.
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
                    "token.actions.githubusercontent.com:sub": "repo:${GITHUB_OWNER}/${GITHUB_REPOSITORY}:environment:production",
                    "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
                }
            }
        }
    ]
}
EOF
)

    if [[ -n "$ROLE_ARN" && "$ROLE_ARN" != "None" ]]; then

        echo "GitHub Actions role already exists. Updating trust policy..."

        aws iam update-assume-role-policy \
            --role-name "$ROLE_NAME" \
            --policy-document "$TRUST_POLICY"

    else

        echo "Creating GitHub Actions role..."

        ROLE_ARN=$(aws iam create-role \
            --role-name "$ROLE_NAME" \
            --assume-role-policy-document "$TRUST_POLICY" \
            --tags \
                Key=Project,Value="$PROJECT_NAME" \
                Key=Environment,Value="$ENVIRONMENT" \
                Key=ManagedBy,Value="$MANAGED_BY" \
            --query "Role.Arn" \
            --output text)

    fi

    append_output "GITHUB_ACTIONS_ROLE_ARN" "$ROLE_ARN"

}

create_inline_policy() {

    local ROLE_NAME="$GITHUB_ACTIONS_ROLE_NAME"

    ECS_EXECUTION_ROLE_ARN=$(require_output ECS_TASK_EXECUTION_ROLE_ARN)

    ECS_TASK_ROLE_ARN=$(require_output ECS_TASK_ROLE_ARN)

    SECRET_ARN=$(require_output RDS_SECRET_ARN)

    # Optional - only present if infra/wompi.env existed when 08-secrets.sh
    # last ran. Fall back to the DB secret ARN (a harmless duplicate in the
    # Resource array) so this script stays idempotent either way.
    WOMPI_SECRET_ARN=$(grep "^WOMPI_SECRET_ARN=" "$SCRIPT_DIR/../outputs.env" | cut -d= -f2- || true)
    WOMPI_SECRET_ARN="${WOMPI_SECRET_ARN:-$SECRET_ARN}"

    # Same pattern for Firebase: deploy.yml's "Validate Secrets Manager secret"
    # step calls describe-secret on RDS, Wompi AND Firebase, so all three ARNs
    # must be in the Resource array below.
    FIREBASE_SECRET_ARN=$(grep "^FIREBASE_SECRET_ARN=" "$SCRIPT_DIR/../outputs.env" | cut -d= -f2- || true)
    FIREBASE_SECRET_ARN="${FIREBASE_SECRET_ARN:-$SECRET_ARN}"

    ACCOUNT_ID=$(aws sts get-caller-identity \
        --query Account \
        --output text)

    # Must match the S3_BUCKET repo variable used by deploy.yml (default
    # mirrors infra/s3/config.sh). Override via env if the bucket differs.
    S3_BUCKET="${S3_BUCKET:-${PROJECT_NAME}-files-prod}"

    # This policy must cover every AWS call .github/workflows/deploy.yml makes,
    # across both the "validate-environment" job (read-only checks) and the
    # "deploy" job (build/push/register/update-service/DNS). Kept scoped to
    # what the workflow actually calls; secretsmanager:GetSecretValue is
    # intentionally NOT here — only the ECS execution role needs it, to
    # resolve the container definition's "secrets" block at task launch.
    POLICY=$(cat <<EOF
{
    "Version": "2012-10-17",
    "Statement": [

        {
            "Sid": "SecretsManagerValidate",
            "Effect": "Allow",
            "Action": [
                "secretsmanager:DescribeSecret"
            ],
            "Resource": [
                "${SECRET_ARN}",
                "${WOMPI_SECRET_ARN}",
                "${FIREBASE_SECRET_ARN}"
            ]
        },

        {
            "Sid": "S3ValidateBucket",
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket"
            ],
            "Resource": "arn:aws:s3:::${S3_BUCKET}"
        },

        {
            "Sid": "SqsValidate",
            "Effect": "Allow",
            "Action": [
                "sqs:GetQueueAttributes"
            ],
            "Resource": "arn:aws:sqs:${AWS_REGION}:${ACCOUNT_ID}:${PROJECT_NAME}-receipt-ocr-*"
        },

        {
            "Sid": "SnsValidate",
            "Effect": "Allow",
            "Action": [
                "sns:GetTopicAttributes"
            ],
            "Resource": "arn:aws:sns:${AWS_REGION}:${ACCOUNT_ID}:${PROJECT_NAME}-receipt-ocr-*"
        },

        {
            "Sid": "ECRAuth",
            "Effect": "Allow",
            "Action": [
                "ecr:GetAuthorizationToken"
            ],
            "Resource": "*"
        },

        {
            "Sid": "ECRPushAndValidate",
            "Effect": "Allow",
            "Action": [
                "ecr:BatchCheckLayerAvailability",
                "ecr:CompleteLayerUpload",
                "ecr:DescribeRepositories",
                "ecr:GetDownloadUrlForLayer",
                "ecr:InitiateLayerUpload",
                "ecr:PutImage",
                "ecr:UploadLayerPart"
            ],
            "Resource": "arn:aws:ecr:${AWS_REGION}:${ACCOUNT_ID}:repository/${ECR_REPOSITORY}"
        },

        {
            "Sid": "ECSDeploy",
            "Effect": "Allow",
            "Action": [
                "ecs:RegisterTaskDefinition",
                "ecs:DescribeTaskDefinition",
                "ecs:DescribeClusters",
                "ecs:DescribeServices",
                "ecs:CreateService",
                "ecs:UpdateService",
                "ecs:ListTasks",
                "ecs:DescribeTasks",
                "ecs:TagResource"
            ],
            "Resource": "*"
        },

        {
            "Sid": "EC2NetworkingValidate",
            "Effect": "Allow",
            "Action": [
                "ec2:DescribeSubnets",
                "ec2:DescribeSecurityGroups",
                "ec2:DescribeNetworkInterfaces"
            ],
            "Resource": "*"
        },

        {
            "Sid": "CloudWatchValidate",
            "Effect": "Allow",
            "Action": [
                "logs:DescribeLogGroups"
            ],
            "Resource": "*"
        },

        {
            "Sid": "TextractRoleValidate",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole"
            ],
            "Resource": "arn:aws:iam::${ACCOUNT_ID}:role/${PROJECT_NAME}-textract-sns-role"
        },

        {
            "Sid": "IAMValidateAndPass",
            "Effect": "Allow",
            "Action": [
                "iam:GetRole",
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