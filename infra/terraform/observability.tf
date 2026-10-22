variable "alert_email" {
  type        = string
  description = "Email que recibe las alarmas de CloudWatch (SNS)."
}

variable "latency_p95_threshold_us" {
  type        = number
  default     = 1500000
  description = "Umbral de latencia p95 en microsegundos. Valor inicial, ajustar tras medir."
}

locals {
  app_log_group = aws_cloudwatch_log_group.application.name
  metric_ns     = "BFlow/Http"
  ecs_dims = {
    ClusterName = "bflow-cluster"
    ServiceName = "bflow-service"
  }
}

# ---------- SNS ----------
resource "aws_sns_topic" "alerts" {
  name = "bflow-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ---------- Metric filters sobre el access log (JSON) ----------
resource "aws_cloudwatch_log_metric_filter" "http_5xx" {
  name           = "bflow-http-5xx"
  log_group_name = local.app_log_group
  pattern        = "{ $.type = \"access\" && $.path = \"/api/*\" && $.status >= 500 }"

  metric_transformation {
    name          = "Http5xx"
    namespace     = local.metric_ns
    value         = "1"
    unit          = "Count"
    default_value = "0"
  }
}

resource "aws_cloudwatch_log_metric_filter" "http_latency" {
  name           = "bflow-http-latency"
  log_group_name = local.app_log_group
  pattern        = "{ $.type = \"access\" && $.path = \"/api/*\" }"

  metric_transformation {
    name      = "HttpLatencyUs"
    namespace = local.metric_ns
    value     = "$.us"
    unit      = "Microseconds"
  }
}

# Latido: cualquier línea de access log (incluye el probe GET /).
resource "aws_cloudwatch_log_metric_filter" "access_lines" {
  name           = "bflow-access-lines"
  log_group_name = local.app_log_group
  pattern        = "{ $.type = \"access\" }"

  metric_transformation {
    name      = "AccessLines"
    namespace = local.metric_ns
    value     = "1"
    unit      = "Count"
  }
}

# ---------- Alarmas ----------
resource "aws_cloudwatch_metric_alarm" "http_5xx" {
  alarm_name          = "bflow-http-5xx"
  alarm_description   = "5xx persistentes en la API (>=3 por 5 min, 2 de 3 periodos)."
  namespace           = local.metric_ns
  metric_name         = "Http5xx"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 3
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "http_latency_p95" {
  alarm_name          = "bflow-http-latency-p95"
  alarm_description   = "Latencia p95 de la API sobre el umbral durante 15 min."
  namespace           = local.metric_ns
  metric_name         = "HttpLatencyUs"
  extended_statistic  = "p95"
  unit                = "Microseconds"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = var.latency_p95_threshold_us
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 6 periodos de 5 min: el arranque satura la CPU ~5-10 min en cada deploy.
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "bflow-cpu-high"
  alarm_description   = "CPU del servicio >80% durante 30 min."
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = local.ecs_dims
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 6
  datapoints_to_alarm = 6
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "memory_high" {
  alarm_name          = "bflow-memory-high"
  alarm_description   = "Memoria del servicio >85% durante 15 min."
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  dimensions          = local.ecs_dims
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# ---------- Dashboard ----------
resource "aws_cloudwatch_dashboard" "bflow" {
  dashboard_name = "bflow-overview"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title   = "Requests (access log) y 5xx API"
          region  = var.aws_region
          view    = "timeSeries"
          stat    = "Sum"
          period  = 300
          metrics = [[local.metric_ns, "AccessLines"], [local.metric_ns, "Http5xx"]]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
        properties = {
          title  = "Latencia API (microsegundos)"
          region = var.aws_region
          view   = "timeSeries"
          period = 300
          metrics = [
            [local.metric_ns, "HttpLatencyUs", { stat = "p50" }],
            [local.metric_ns, "HttpLatencyUs", { stat = "p95" }]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6
        properties = {
          title   = "CPU (%)"
          region  = var.aws_region
          view    = "timeSeries"
          stat    = "Average"
          period  = 300
          metrics = [["AWS/ECS", "CPUUtilization", "ClusterName", "bflow-cluster", "ServiceName", "bflow-service"]]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6
        properties = {
          title   = "Memoria (%)"
          region  = var.aws_region
          view    = "timeSeries"
          stat    = "Average"
          period  = 300
          metrics = [["AWS/ECS", "MemoryUtilization", "ClusterName", "bflow-cluster", "ServiceName", "bflow-service"]]
        }
      }
    ]
  })
}

data "aws_caller_identity" "alerts" {}

resource "aws_cloudwatch_event_rule" "task_failed" {
  name        = "bflow-ecs-task-failed"
  description = "Tasks de bflow-service detenidas por fallo (no por reemplazo de deploy)."
  event_pattern = jsonencode({
    source        = ["aws.ecs"]
    "detail-type" = ["ECS Task State Change"]
    detail = {
      clusterArn = [aws_ecs_cluster.main.arn]
      group      = ["service:bflow-service"]
      lastStatus = ["STOPPED"]
      stopCode   = ["EssentialContainerExited", "TaskFailedToStart"]
    }
  })
}

resource "aws_cloudwatch_event_target" "task_failed_sns" {
  rule = aws_cloudwatch_event_rule.task_failed.name
  arn  = aws_sns_topic.alerts.arn
}

resource "aws_sns_topic_policy" "alerts" {
  arn = aws_sns_topic.alerts.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowAccountPublish"
        Effect    = "Allow"
        Principal = { AWS = "*" }
        Action    = "SNS:Publish"
        Resource  = aws_sns_topic.alerts.arn
        Condition = { StringEquals = { "AWS:SourceOwner" = data.aws_caller_identity.alerts.account_id } }
      },
      {
        Sid       = "AllowEventBridgePublish"
        Effect    = "Allow"
        Principal = { Service = "events.amazonaws.com" }
        Action    = "SNS:Publish"
        Resource  = aws_sns_topic.alerts.arn
        Condition = { ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.task_failed.arn } }
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "dns_sync" {
  name              = "/aws/lambda/bflow-dns-sync"
  retention_in_days = var.log_retention_days

  lifecycle {
    ignore_changes = [tags]
  }
}