terraform {
  required_version = ">= 1.9.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  common_secret_arns = compact([
    aws_secretsmanager_secret.database.arn,
    aws_secretsmanager_secret.wompi.arn,
    aws_secretsmanager_secret.firebase.arn
  ])
}
