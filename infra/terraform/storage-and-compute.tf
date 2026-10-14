resource "aws_ecr_repository" "backend" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  count      = var.ecr_lifecycle_max_images == null ? 0 : 1
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the most recent 20 deploy images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.ecr_lifecycle_max_images
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "application" {
  name              = var.cloudwatch_log_group_name
  retention_in_days = var.log_retention_days

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_ecs_cluster" "main" {
  name = var.ecs_cluster_name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  lifecycle {
    ignore_changes = [tags]
  }
}

resource "aws_s3_bucket" "files" {
  bucket        = var.s3_bucket_name
  force_destroy = false

  lifecycle {
    # force_destroy governs Terraform's deletion behavior, not bucket settings.
    ignore_changes = [force_destroy, tags]
  }
}

resource "aws_s3_bucket_public_access_block" "files" {
  bucket                  = aws_s3_bucket.files.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "files" {
  bucket = aws_s3_bucket.files.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "files" {
  bucket = aws_s3_bucket.files.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_versioning" "files" {
  bucket = aws_s3_bucket.files.id
  versioning_configuration { status = "Suspended" }
}

resource "aws_s3_bucket_lifecycle_configuration" "files" {
  bucket = aws_s3_bucket.files.id
  rule {
    id     = "expire-tmp-objects"
    status = "Enabled"
    filter { prefix = var.temporary_upload_prefix }
    expiration { days = var.temporary_upload_expiration_days }
  }
}

resource "aws_s3_bucket_cors_configuration" "files" {
  count  = length(var.frontend_origins) > 0 ? 1 : 0
  bucket = aws_s3_bucket.files.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_origins = var.frontend_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_db_subnet_group" "main" {
  count      = var.db_provider == "rds" ? 1 : 0
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgres" {
  count                       = var.db_provider == "rds" ? 1 : 0
  identifier                  = var.rds_instance_identifier
  engine                      = "postgres"
  engine_version              = var.rds_engine_version
  instance_class              = var.rds_instance_class
  allocated_storage           = var.rds_allocated_storage_gb
  storage_type                = "gp3"
  db_name                     = var.rds_database_name
  username                    = var.rds_master_username
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.main[0].name
  vpc_security_group_ids      = [aws_security_group.rds[0].id]
  backup_retention_period     = var.rds_backup_retention_days
  publicly_accessible         = false
  storage_encrypted           = true
  multi_az                    = false
  deletion_protection         = false
  skip_final_snapshot         = true

  lifecycle {
    precondition {
      condition     = var.db_provider == "rds"
      error_message = "RDS is opt-in; keep db_provider=supabase for the pre-revenue default."
    }
  }
}

# Terraform owns the secret containers and IAM access, never their values.
# Supply values through the Secrets Manager console/CLI so local Terraform state
# cannot disclose database, Wompi, Firebase, or Cloudflare credentials.
resource "aws_secretsmanager_secret" "database" {
  name                    = "${var.project_name}/database"
  recovery_window_in_days = 7

  lifecycle {
    ignore_changes = [description, force_overwrite_replica_secret, recovery_window_in_days, tags]
  }
}

resource "aws_secretsmanager_secret" "wompi" {
  name                    = "${var.project_name}/wompi"
  recovery_window_in_days = 7

  lifecycle {
    ignore_changes = [description, force_overwrite_replica_secret, recovery_window_in_days, tags]
  }
}

resource "aws_secretsmanager_secret" "firebase" {
  name                    = "${var.project_name}/firebase"
  recovery_window_in_days = 7

  lifecycle {
    ignore_changes = [description, force_overwrite_replica_secret, recovery_window_in_days, tags]
  }
}

resource "aws_budgets_budget" "monthly" {
  name         = "${var.project_name}-monthly-budget"
  budget_type  = "COST"
  limit_amount = var.budget_limit_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = length(var.budget_notification_emails) == 0 ? [] : [50, 80, 100]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = var.budget_notification_emails
    }
  }

  lifecycle {
    ignore_changes = [tags]
  }
}
