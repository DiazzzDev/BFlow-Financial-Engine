resource "aws_secretsmanager_secret" "cloudflare_dns" {
  count                   = var.dns_sync_enabled ? 1 : 0
  name                    = "${var.project_name}/cloudflare-dns"
  recovery_window_in_days = 7
}

data "aws_iam_policy_document" "dns_sync_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "dns_sync" {
  count              = var.dns_sync_enabled ? 1 : 0
  name               = "${var.project_name}-dns-sync-lambda-role"
  assume_role_policy = data.aws_iam_policy_document.dns_sync_trust.json
}

resource "aws_iam_role_policy" "dns_sync" {
  count = var.dns_sync_enabled ? 1 : 0
  name  = "${var.project_name}-dns-sync-access"
  role  = aws_iam_role.dns_sync[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecs:ListTasks", "ecs:DescribeTasks", "ec2:DescribeNetworkInterfaces"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = aws_secretsmanager_secret.cloudflare_dns[0].arn
      },
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_lambda_function" "dns_sync" {
  count            = var.dns_sync_enabled ? 1 : 0
  function_name    = "${var.project_name}-dns-sync"
  role             = aws_iam_role.dns_sync[0].arn
  filename         = var.dns_sync_zip_path
  source_code_hash = filebase64sha256(var.dns_sync_zip_path)
  handler          = "bootstrap"
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  timeout          = 15
  memory_size      = 128

  environment {
    variables = {
      ECS_CLUSTER_NAME      = var.ecs_cluster_name
      ECS_SERVICE_NAME      = var.ecs_service_name
      CLOUDFLARE_SECRET_ARN = aws_secretsmanager_secret.cloudflare_dns[0].arn
    }
  }
}

resource "aws_cloudwatch_event_rule" "ecs_task_running" {
  count       = var.dns_sync_enabled ? 1 : 0
  name        = "${var.project_name}-ecs-task-state-change"
  description = "Sync Cloudflare DNS when the ECS task receives a public IP"
  event_pattern = jsonencode({
    source      = ["aws.ecs"]
    detail-type = ["ECS Task State Change"]
    detail = {
      clusterArn = [aws_ecs_cluster.main.arn]
      lastStatus = ["RUNNING"]
      launchType = ["FARGATE"]
    }
  })
}

resource "aws_cloudwatch_event_target" "dns_sync" {
  count = var.dns_sync_enabled ? 1 : 0
  rule  = aws_cloudwatch_event_rule.ecs_task_running[0].name
  arn   = aws_lambda_function.dns_sync[0].arn
}

resource "aws_lambda_permission" "eventbridge" {
  count         = var.dns_sync_enabled ? 1 : 0
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dns_sync[0].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.ecs_task_running[0].arn
}
