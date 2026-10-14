variable "aws_region" {
  type        = string
  description = "AWS region for the BFlow infrastructure."
  default     = "us-east-1"
}

variable "project_name" {
  type        = string
  description = "Short project identifier used in resource names."
  default     = "bflow"
}

variable "environment" {
  type        = string
  description = "Deployment environment tag."
  default     = "production"
}

variable "managed_by" {
  type        = string
  description = "Value of the ManagedBy tag. Keep the current value while importing existing resources."
  default     = "terraform"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "availability_zones" {
  type        = list(string)
  description = "Two AZs used for Fargate and the optional RDS subnet group."
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.10.0/24", "10.0.20.0/24"]
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "ecs_ingress_rules" {
  description = "Ingress rules retained exactly during migration; replace with a load-balancer policy later."
  type = list(object({
    description = string
    from_port   = number
    to_port     = number
    cidr_blocks = list(string)
  }))
  default = [{
    description = "Application HTTP"
    from_port   = 8080
    to_port     = 8080
    cidr_blocks = ["0.0.0.0/0"]
  }]
}

variable "ecr_repository_name" {
  type    = string
  default = "bflow-backend"
}

variable "ecr_lifecycle_max_images" {
  type        = number
  description = "Set after migration to retain only this many images. Null preserves the current repository policy state."
  default     = null
  nullable    = true
}

variable "ecs_cluster_name" {
  type    = string
  default = "bflow-cluster"
}

variable "ecs_service_name" {
  type    = string
  default = "bflow-service"
}

variable "ecs_task_family" {
  type    = string
  default = "bflow-backend"
}

variable "ecs_container_name" {
  type    = string
  default = "bflow-backend"
}

variable "textract_sns_role_name" {
  type        = string
  description = "Existing Textract role name; preserves the production publisher role during import."
  default     = "bflow-textract-sns-publisher"
}

variable "cloudwatch_log_group_name" {
  type    = string
  default = "/ecs/bflow"
}

variable "log_retention_days" {
  type        = number
  description = "Short retention keeps early-stage logging costs bounded."
  default     = 7
}

variable "s3_bucket_name" {
  type    = string
  default = "bflow-files-dev"
}

variable "frontend_origins" {
  type        = list(string)
  description = "Origins allowed to upload/download private files directly."
  default     = []
}

variable "temporary_upload_prefix" {
  type    = string
  default = "tmp/"
}

variable "temporary_upload_expiration_days" {
  type    = number
  default = 1
}

variable "s3_object_prefix" {
  type        = string
  description = "Only this prefix is accessible to ECS tasks."
  default     = "users/*"
}

variable "db_provider" {
  type        = string
  description = "supabase (default, no AWS database cost) or rds."
  default     = "supabase"

  validation {
    condition     = contains(["supabase", "rds"], var.db_provider)
    error_message = "db_provider must be supabase or rds."
  }
}

variable "rds_instance_identifier" {
  type    = string
  default = "bflow-postgres"
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "rds_engine_version" {
  type    = string
  default = "16"
}

variable "rds_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "rds_database_name" {
  type    = string
  default = "bflow"
}

variable "rds_master_username" {
  type    = string
  default = "bflow_admin"
}

variable "rds_backup_retention_days" {
  type    = number
  default = 1
}

variable "budget_limit_usd" {
  type    = string
  default = "20"
}

variable "budget_notification_emails" {
  type        = list(string)
  description = "Recipients for budget notifications. Empty preserves an existing budget without notifications."
  default     = []
}

variable "github_owner" {
  type    = string
  default = "DiazzzDev"
}

variable "github_repository" {
  type    = string
  default = "BFlow-Financial-Engine"
}

variable "github_environment" {
  type    = string
  default = "production"
}

variable "github_actions_role_name" {
  type    = string
  default = "bflow-github-actions-role"
}

variable "cognito_user_pool_id" {
  type        = string
  description = "Existing Cognito user pool. Terraform creates the mobile client only when enabled."
  default     = null
  nullable    = true
}

variable "manage_cognito_client" {
  type        = bool
  description = "Opt-in because the user pool predates this Terraform migration."
  default     = false
}

variable "dns_sync_enabled" {
  type        = bool
  description = "Creates the existing low-cost EventBridge/Lambda DNS updater after its zip and secret exist."
  default     = false
}

variable "dns_sync_zip_path" {
  type        = string
  description = "Path to the built Go Lambda zip, relative to this Terraform directory."
  default     = "../dns-sync/.build/lambda_function.zip"
}
