resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["22ff89586561fc2d52f77491e9f1eff1b80be33e"]

  lifecycle {
    ignore_changes = [tags]
  }
}

data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_owner}/${var.github_repository}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = var.github_actions_role_name
  assume_role_policy = data.aws_iam_policy_document.github_actions_trust.json

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "${var.project_name}-github-deploy-policy"
  role = aws_iam_role.github_actions.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SecretsManagerValidate"
        Effect   = "Allow"
        Action   = "secretsmanager:DescribeSecret"
        Resource = local.common_secret_arns
      },
      {
        Sid      = "S3ValidateBucket"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.files.arn
      },
      {
        Sid      = "SqsValidate"
        Effect   = "Allow"
        Action   = "sqs:GetQueueAttributes"
        Resource = [aws_sqs_queue.ocr_requests.arn, aws_sqs_queue.ocr_results.arn]
      },
      {
        Sid      = "SnsValidate"
        Effect   = "Allow"
        Action   = "sns:GetTopicAttributes"
        Resource = aws_sns_topic.ocr_results.arn
      },
      {
        Sid      = "ECRAuth"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "ECRPushAndValidate"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload", "ecr:DescribeImages",
          "ecr:DescribeRepositories", "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart"
        ]
        Resource = aws_ecr_repository.backend.arn
      },
      {
        Sid      = "ECSDeploy"
        Effect   = "Allow"
        Action   = ["ecs:RegisterTaskDefinition", "ecs:DescribeTaskDefinition", "ecs:DescribeClusters", "ecs:DescribeServices", "ecs:CreateService", "ecs:UpdateService", "ecs:ListTasks", "ecs:DescribeTasks", "ecs:TagResource"]
        Resource = "*"
      },
      {
        Sid      = "EC2AndCloudWatchValidation"
        Effect   = "Allow"
        Action   = ["ec2:DescribeSubnets", "ec2:DescribeSecurityGroups", "ec2:DescribeNetworkInterfaces", "logs:DescribeLogGroups"]
        Resource = "*"
      },
      {
        Sid      = "TextractRoleValidate"
        Effect   = "Allow"
        Action   = "iam:GetRole"
        Resource = aws_iam_role.textract_sns.arn
      },
      {
        Sid      = "IAMValidateAndPassEcsRoles"
        Effect   = "Allow"
        Action   = ["iam:GetRole", "iam:PassRole"]
        Resource = [aws_iam_role.ecs_execution.arn, aws_iam_role.ecs_task.arn]
      }
    ]
  })
}

resource "aws_cognito_user_pool_client" "mobile" {
  count = var.manage_cognito_client ? 1 : 0

  name                                 = "${var.project_name}-mobile-client"
  user_pool_id                         = var.cognito_user_pool_id
  generate_secret                      = false
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  callback_urls                        = ["bflow://auth/callback"]
  logout_urls                          = ["bflow://auth/logout"]
  supported_identity_providers         = ["COGNITO", "Google"]
  explicit_auth_flows                  = ["ALLOW_REFRESH_TOKEN_AUTH"]
  prevent_user_existence_errors        = "ENABLED"
  enable_token_revocation              = true
  access_token_validity                = 60
  id_token_validity                    = 60
  refresh_token_validity               = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  lifecycle {
    precondition {
      condition     = var.cognito_user_pool_id != null
      error_message = "cognito_user_pool_id is required when manage_cognito_client is true."
    }
  }
}

output "github_actions_role_arn" {
  value       = aws_iam_role.github_actions.arn
  description = "Set as the AWS_ROLE_ARN production environment secret."
}

output "deployment_variables" {
  description = "Non-sensitive GitHub production variables required by deploy.yml."
  value = {
    AWS_REGION                     = var.aws_region
    ECR_REPOSITORY                 = aws_ecr_repository.backend.repository_url
    ECS_CLUSTER_NAME               = aws_ecs_cluster.main.name
    ECS_CONTAINER_NAME             = var.ecs_container_name
    ECS_SERVICE_NAME               = var.ecs_service_name
    ECS_TASK_FAMILY                = var.ecs_task_family
    ECS_EXECUTION_ROLE_ARN         = aws_iam_role.ecs_execution.arn
    ECS_TASK_ROLE_ARN              = aws_iam_role.ecs_task.arn
    CLOUDWATCH_LOG_GROUP           = aws_cloudwatch_log_group.application.name
    ECS_PUBLIC_SUBNET_1            = aws_subnet.public[0].id
    ECS_PUBLIC_SUBNET_2            = aws_subnet.public[1].id
    ECS_SECURITY_GROUP             = aws_security_group.ecs.id
    S3_BUCKET                      = aws_s3_bucket.files.id
    RECEIPT_OCR_REQUESTS_QUEUE_URL = aws_sqs_queue.ocr_requests.id
    RECEIPT_OCR_RESULTS_QUEUE_URL  = aws_sqs_queue.ocr_results.id
    RECEIPT_OCR_RESULTS_TOPIC_ARN  = aws_sns_topic.ocr_results.arn
    TEXTRACT_SNS_ROLE_ARN          = aws_iam_role.textract_sns.arn
    COGNITO_USER_POOL_ID           = var.cognito_user_pool_id
    COGNITO_ISSUER_URI             = var.cognito_user_pool_id == null ? null : "https://cognito-idp.${var.aws_region}.amazonaws.com/${var.cognito_user_pool_id}"
  }
}

output "secret_arns" {
  description = "Populate the three matching GitHub environment secrets with these ARNs."
  sensitive   = true
  value = {
    RDS_SECRET_ARN      = aws_secretsmanager_secret.database.arn
    WOMPI_SECRET_ARN    = aws_secretsmanager_secret.wompi.arn
    FIREBASE_SECRET_ARN = aws_secretsmanager_secret.firebase.arn
  }
}
