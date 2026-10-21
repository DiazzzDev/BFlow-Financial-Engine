data "aws_iam_policy_document" "ecs_task_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_execution" {
  name               = "${var.project_name}-ecs-task-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_trust.json

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "${var.project_name}-secrets-access"
  role = aws_iam_role.ecs_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = local.common_secret_arns
    }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name               = "${var.project_name}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_trust.json

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_sqs_queue" "ocr_requests" {
  name                       = "${var.project_name}-receipt-ocr-requests"
  visibility_timeout_seconds = 120
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ocr_requests_dlq.arn
    maxReceiveCount     = 5
  })

  lifecycle {
    ignore_changes = [max_message_size, tags]
  }
}

resource "aws_sqs_queue" "ocr_requests_dlq" {
  name                      = "${var.project_name}-receipt-ocr-requests-dlq"
  message_retention_seconds = 345600
  sqs_managed_sse_enabled   = true

  lifecycle {
    ignore_changes = [max_message_size, tags]
  }
}

resource "aws_sqs_queue" "ocr_results" {
  name                       = "${var.project_name}-receipt-ocr-results"
  visibility_timeout_seconds = 120
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ocr_results_dlq.arn
    maxReceiveCount     = 5
  })

  lifecycle {
    ignore_changes = [max_message_size, tags]
  }
}

resource "aws_sqs_queue" "ocr_results_dlq" {
  name                      = "${var.project_name}-receipt-ocr-results-dlq"
  message_retention_seconds = 345600
  sqs_managed_sse_enabled   = true

  lifecycle {
    ignore_changes = [max_message_size, tags]
  }
}

resource "aws_sns_topic" "ocr_results" {
  name = "${var.project_name}-receipt-ocr-results"

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_sns_topic_subscription" "ocr_results" {
  topic_arn = aws_sns_topic.ocr_results.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.ocr_results.arn

  lifecycle {
    # These are provider-side defaults, not desired SNS behavior changes.
    ignore_changes = [confirmation_timeout_in_minutes, endpoint_auto_confirms]
  }
}

resource "aws_sqs_queue_policy" "ocr_results" {
  queue_url = aws_sqs_queue.ocr_results.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowSnsTopicToSendMessage"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.ocr_results.arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.ocr_results.arn } }
    }]
  })
}

data "aws_iam_policy_document" "textract_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["textract.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "textract_sns" {
  name               = var.textract_sns_role_name
  assume_role_policy = data.aws_iam_policy_document.textract_trust.json

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_iam_role_policy" "textract_publish" {
  name = "publish-to-ocr-results-topic"
  role = aws_iam_role.textract_sns.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sns:Publish", Resource = aws_sns_topic.ocr_results.arn }]
  })
}

resource "aws_iam_role_policy" "ecs_task_ocr" {
  name = "receipt-ocr-messaging-policy"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "OcrRequestsQueueAccess"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.ocr_requests.arn
      },
      {
        Sid      = "OcrResultsQueueAccess"
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.ocr_results.arn
      },
      {
        Sid      = "TextractStartAndGetExpenseAnalysis"
        Effect   = "Allow"
        Action   = ["textract:StartExpenseAnalysis", "textract:GetExpenseAnalysis"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy" "ecs_task_s3" {
  name = "s3-access-policy"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "S3ObjectAccess"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.files.arn}/${var.s3_object_prefix}"
      },
      {
        Sid       = "S3ListBucket"
        Effect    = "Allow"
        Action    = "s3:ListBucket"
        Resource  = aws_s3_bucket.files.arn
        Condition = { StringLike = { "s3:prefix" = [var.s3_object_prefix] } }
      }
    ]
  })
}

# Harmless while the agent is disabled; the only switch is the OTEL_ENABLED GitHub Actions variable.
resource "aws_iam_role_policy" "ecs_task_otel" {
  name = "bflow-otel-traces"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "PutTraceSegments"
      Effect   = "Allow"
      Action   = ["xray:PutTraceSegments"]
      Resource = "*"
    }]
  })
}
