# AWS Infrastructure

This directory contains the complete Infrastructure as Code (IaC) for deploying the BFlow Backend on AWS using Bash scripts and the AWS CLI.

The infrastructure is designed to be:

- Idempotent
- Fully reproducible
- Low-cost
- Easy to understand
- Independent from Terraform or CloudFormation

Every bootstrap script can be executed multiple times without creating duplicated resources.

---

# Architecture

```
                         GitHub
                            │
                            │ Push to main
                            ▼
                   GitHub Actions (OIDC)
                            │
                            ▼
                 IAM Deploy Role (OIDC)
                            │
        ┌───────────────────┴────────────────────┐
        │                                        │
        ▼                                        ▼
    Amazon ECR                          Amazon ECS Fargate
        │                                        │
        │ Docker Image                           │
        └────────────────────────────────────────┘
                            │
                            ▼
                     Spring Boot Container
                            │
                ┌───────────┴────────────┐
                │                        │
                ▼                        ▼
         Secrets Manager          CloudWatch Logs
                │
                ▼
         Amazon RDS PostgreSQL
```

---

# Infrastructure Components

| Service | Purpose |
|----------|----------|
| VPC | Private virtual network |
| Public Subnets | ECS Tasks |
| Private Subnets | PostgreSQL Database |
| Internet Gateway | Internet access |
| Route Tables | Network routing |
| Security Groups | Firewall rules |
| Amazon ECR | Docker image registry |
| Amazon ECS Fargate | Container orchestration |
| Amazon RDS PostgreSQL | Relational database |
| Secrets Manager | Database credentials |
| IAM | Permissions |
| CloudWatch | Logs |
| GitHub OIDC | Secure CI/CD authentication |
| AWS Budget | Monthly cost monitoring |

---

# Bootstrap Scripts

The infrastructure is provisioned sequentially.

| Script | Creates | Depends On |
|---------|----------|------------|
| 01-vpc.sh | VPC | - |
| 02-subnets.sh | Public and Private Subnets | VPC |
| 03-internet-gateway.sh | Internet Gateway | VPC |
| 04-route-tables.sh | Route Tables and Associations | VPC, IGW |
| 05-security-groups.sh | ECS and RDS Security Groups | VPC |
| 06-ecr.sh | ECR Repository | - |
| 07-rds.sh | PostgreSQL Database | Network |
| 08-secrets.sh | Secrets Manager Secret | RDS |
| 09-iam.sh | ECS IAM Roles | Secrets |
| 10-cloudwatch.sh | Log Group | - |
| 11-ecs.sh | ECS Cluster, Task Definition and Service | Everything above |
| 12-github-oidc.sh | GitHub Deployment Role | IAM |
| 13-budget.sh | AWS Budget | - |

---

# Bootstrap Order

Run the scripts in the following order:

```bash
./bootstrap/01-vpc.sh
./bootstrap/02-subnets.sh
./bootstrap/03-internet-gateway.sh
./bootstrap/04-route-tables.sh
./bootstrap/05-security-groups.sh
./bootstrap/06-ecr.sh
./bootstrap/07-rds.sh
./bootstrap/08-secrets.sh
./bootstrap/09-iam.sh
./bootstrap/10-cloudwatch.sh
./bootstrap/11-ecs.sh
./bootstrap/12-github-oidc.sh
./bootstrap/13-budget.sh
```

Each script stores its outputs into:

```
outputs.env
```

The following scripts automatically consume those outputs.

---

# Configuration Files

## config.env

Contains user configurable values.

Examples:

- AWS Region
- CIDR blocks
- ECS CPU
- ECS Memory
- Database name
- Budget limit
- ECS cluster names

This file defines the desired infrastructure.

---

## outputs.env

Contains generated AWS resource identifiers.

Examples:

- VPC ID
- Subnet IDs
- Security Group IDs
- ECS Cluster
- IAM Role ARNs
- ECR Repository URI

This file is generated automatically.

It should never be edited manually.

---

# GitHub Actions

Deployment uses GitHub OpenID Connect (OIDC).

No AWS Access Keys are stored inside GitHub.

Authentication flow:

```
GitHub Actions
        │
        ▼
OIDC Token
        │
        ▼
AWS IAM Role
        │
        ▼
Temporary Credentials
```

This is the AWS recommended authentication mechanism.

---

# Required GitHub Variables

Repository Variables:

| Variable | Example |
|----------|---------|
| AWS_REGION | us-east-1 |
| AWS_ROLE_ARN | arn:aws:iam::123456789012:role/bflow-github-actions-role |
| ECR_REPOSITORY | bflow-backend |
| ECS_CLUSTER_NAME | bflow-cluster |
| ECS_SERVICE_NAME | bflow-service |
| ECS_TASK_FAMILY | bflow-backend |
| ECS_CONTAINER_NAME | bflow-backend |

No AWS secrets are required.

---

# Deployment Flow

A deployment starts after pushing to the `main` branch.

```
Developer
      │
      ▼
Push to main
      │
      ▼
GitHub Actions
      │
      ▼
Build Maven Project
      │
      ▼
Build Docker Image
      │
      ▼
Push Image to Amazon ECR
      │
      ▼
Register New ECS Task Definition
      │
      ▼
Update ECS Service
      │
      ▼
Rolling Deployment
```

---

# Why ECS Fargate?

Fargate removes the need to manage EC2 instances.

Benefits:

- No server maintenance
- Automatic container isolation
- Simple deployments
- Pay only for running tasks
- Easy scaling

---

# Why RDS in Private Subnets?

The database is intentionally isolated.

Only ECS Security Groups are allowed to connect.

The database has:

- No public IP
- Encrypted storage
- Private networking
- Security Group restrictions

This reduces the attack surface.

---

# Why Secrets Manager?

Database credentials are never stored inside:

- Docker images
- GitHub
- Source code
- ECS Task Definitions

The application retrieves credentials securely during startup.

---

# Why GitHub OIDC?

Traditional deployments require:

- AWS Access Key
- AWS Secret Key

Those credentials never expire and can be leaked.

OIDC generates temporary credentials during deployment.

Advantages:

- No long-lived credentials
- Automatic expiration
- Least privilege
- AWS recommended approach

---

# Cost Optimization

The infrastructure prioritizes low monthly costs.

Current decisions include:

- ECS Fargate (1 task)
- Single Availability Zone database
- Monthly AWS Budget

This setup is intended for early-stage projects and can be upgraded later.

---

# Future Improvements

Potential production enhancements include:

- Application Load Balancer
- Auto Scaling
- ECS Service Discovery
- NAT Gateway
- AWS WAF
- Multi-AZ RDS
- Read Replicas
- CloudFront
- Route53
- AWS Certificate Manager
- ECS Blue/Green Deployments

None of these are required for an initial production deployment.

---

# Troubleshooting

## ECS service does not start

Verify:

- Task Definition
- Container logs
- Secrets Manager permissions
- IAM Task Role

---

## Database connection fails

Verify:

- Security Groups
- Secret values
- Database endpoint
- ECS Task Role permissions

---

## GitHub deployment fails

Verify:

- OIDC provider
- IAM trust policy
- GitHub repository variables
- GitHub branch name

---

## ECS cannot pull image

Verify:

- ECR repository
- ECS Execution Role
- AmazonECSTaskExecutionRolePolicy

---

# Security Notes

- Database is private.
- IAM follows least privilege.
- Secrets are stored in AWS Secrets Manager.
- GitHub uses OIDC instead of access keys.
- Containers run as a non-root user.
- Docker images are immutable.
- Image scanning is enabled in Amazon ECR.
- CloudWatch retains logs for a limited period.
- Security Groups only expose required ports.

---

# Repository Structure

```
infra/
│
├── bootstrap/
│   ├── 01-vpc.sh
│   ├── 02-subnets.sh
│   ├── 03-internet-gateway.sh
│   ├── 04-route-tables.sh
│   ├── 05-security-groups.sh
│   ├── 06-ecr.sh
│   ├── 07-rds.sh
│   ├── 08-secrets.sh
│   ├── 09-iam.sh
│   ├── 10-cloudwatch.sh
│   ├── 11-ecs.sh
│   ├── 12-github-oidc.sh
│   └── 13-budget.sh
│
├── lib/
│   └── helpers.sh
│
├── config.env
├── outputs.env.example
└── README.md
```

pending to document:


aws iam put-role-policy --role-name bflow-github-actions-role --policy-name bflow-github-deploy-policy --policy-document file://bflow-github-deploy-policy.json
aws iam get-role-policy --role-name bflow-github-actions-role --policy-name bflow-github-deploy-policy

How to replicate
---
adr