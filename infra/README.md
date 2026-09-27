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
| Secrets Manager | Database, Wompi, and Firebase credentials |
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
| 14-ocr-pipeline.sh | Receipt-OCR SQS queues, SNS topic, Textract IAM role, ECS task role grant | IAM (09) |
| 15-dns-sync.sh | Cloudflare secret, Lambda, EventBridge rule that keeps the DNS A record in sync with the ECS task's public IP | ECS (11) |

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
./bootstrap/14-ocr-pipeline.sh
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

## cloudflare.env

Copy `infra/cloudflare.env.example` to `infra/cloudflare.env` and fill it
in to enable `bootstrap/15-dns-sync.sh`. These credentials are stored in
Secrets Manager and read by the dns-sync Lambda — not GitHub Actions.
Without this file, `15-dns-sync.sh` is a no-op.

## Firebase Cloud Messaging

FCM credentials are managed through the AWS CLI bootstrap scripts. Copy
`infra/firebase.env.example` to the ignored `infra/firebase.env`, set the
Firebase project ID, and provide the base64-encoded service-account JSON.
`bootstrap/08-secrets.sh` stores it in Secrets Manager and writes
`FIREBASE_SECRET_ARN` to `outputs.env`; use that ARN as the GitHub Actions
secret of the same name. Set the `FIREBASE_ENABLED` GitHub variable to
`true` only after the secret is available. No Firebase private key is
committed or included in the Docker image.

---

# PostgreSQL Provider (Supabase / RDS)

The database backend is selected by a single variable in `config.env`:

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

`deploy.yml` validates all of these at the start of every run (job
`validate-environment`) and fails fast with a clear message if any is
missing or points at something that doesn't exist in AWS. Set them under
**Settings > Environments > production**.

Repository/Environment Variables (`vars.*`, not secret):

| Variable | Example | Produced by |
|----------|---------|-------------|
| AWS_REGION | us-east-1 | config.env |
| ECR_REPOSITORY | 123456789012.dkr.ecr.us-east-1.amazonaws.com/bflow-backend | bootstrap/06-ecr.sh |
| ECS_CLUSTER_NAME | bflow-cluster | bootstrap/11-ecs.sh |
| ECS_SERVICE_NAME | bflow-service | config.env |
| ECS_TASK_FAMILY | bflow-backend | config.env |
| ECS_CONTAINER_NAME | bflow-backend | config.env |
| ECS_EXECUTION_ROLE_ARN | arn:aws:iam::...:role/bflow-execution-role | bootstrap/09-iam.sh |
| ECS_TASK_ROLE_ARN | arn:aws:iam::...:role/bflow-task-role | bootstrap/09-iam.sh |
| CLOUDWATCH_LOG_GROUP | /ecs/bflow-backend | bootstrap/10-cloudwatch.sh |
| ECS_PUBLIC_SUBNET_1 / _2 | subnet-0abc... | bootstrap/02-subnets.sh |
| ECS_SECURITY_GROUP | sg-0abc... | bootstrap/05-security-groups.sh |
| S3_BUCKET | bflow-prod-receipts | infra/s3/01-create-bucket.sh |
| COGNITO_ISSUER_URI | https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxxxx | Cognito user pool console |
| RECEIPT_OCR_REQUESTS_QUEUE_URL | https://sqs.us-east-1.amazonaws.com/.../bflow-receipt-ocr-requests | bootstrap/14-ocr-pipeline.sh |
| RECEIPT_OCR_RESULTS_QUEUE_URL | https://sqs.us-east-1.amazonaws.com/.../bflow-receipt-ocr-results | bootstrap/14-ocr-pipeline.sh |
| RECEIPT_OCR_RESULTS_TOPIC_ARN | arn:aws:sns:us-east-1:...:bflow-receipt-ocr-results | bootstrap/14-ocr-pipeline.sh |
| TEXTRACT_SNS_ROLE_ARN | arn:aws:iam::...:role/bflow-textract-sns-role | bootstrap/14-ocr-pipeline.sh |
| FIREBASE_ENABLED | false (set to true after configuring FCM) | GitHub Environment variable |

Repository/Environment Secrets (`secrets.*`):

| Secret | Produced by |
|--------|-------------|
| AWS_ROLE_ARN | bootstrap/12-github-oidc.sh |
| RDS_SECRET_ARN | bootstrap/08-secrets.sh |
| WOMPI_SECRET_ARN | bootstrap/08-secrets.sh (requires `infra/wompi.env`, see `infra/wompi.env.example`) |
| FIREBASE_SECRET_ARN | bootstrap/08-secrets.sh (uses `infra/firebase.env`, see `infra/firebase.env.example`) |

> Cloudflare credentials (`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ZONE_ID`,
> `CLOUDFLARE_DNS_RECORD_ID`, `CLOUDFLARE_DNS_RECORD_NAME`) are **not**
> GitHub variables/secrets anymore. They live in Secrets Manager, set up
> once via `infra/cloudflare.env` + `bootstrap/15-dns-sync.sh` — see
> "DNS Sync" below. `deploy.yml` no longer touches Cloudflare at all.

`RECEIPT_OCR_*` and `TEXTRACT_SNS_ROLE_ARN` are wired into the ECS task
definition, but the async Textract trigger in `ReceiptUploadService` is
still a TODO in the codebase (`OCR-04`) — the pipeline is provisioned
ahead of the feature so the deploy stays reproducible once it ships.

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

# DNS Sync (Cloudflare)

There is no ALB, so the `api.bflow-studio.com` DNS record points
directly at the ECS task's public IP. That IP changes on **any** task
replacement, not just deploys — a crash, an OOM kill, a Fargate host
retirement, or a Spot interruption all launch a new task with a new
IP. `deploy.yml` updating DNS after its own deploys was not enough to
cover those cases, so DNS sync was moved out of GitHub Actions
entirely:

```
ECS Task State Change (RUNNING)
            │
            ▼
      EventBridge Rule
            │
            ▼
      Lambda (dns-sync)
            │
   ┌────────┴────────┐
   ▼                 ▼
EC2 DescribeNetworkInterfaces   Secrets Manager (Cloudflare creds)
   │                 │
   └────────┬────────┘
            ▼
   Cloudflare API (update A record)
```

Provisioned by `bootstrap/15-dns-sync.sh` (requires
`infra/cloudflare.env`, see `infra/cloudflare.env.example`, and a local
Go toolchain to compile the Lambda):

- A Secrets Manager secret holding the Cloudflare API token and record
  identifiers.
- A Lambda written in Go (`infra/dns-sync/main.go`), compiled as a
  `provided.al2023` / `arm64` custom runtime binary (`bootstrap`), that
  resolves the new task's public IP and updates the Cloudflare A
  record only if it changed. Go was chosen over an interpreted runtime
  for lower cold starts and lower cost per invocation on arm64.
- An EventBridge rule matching `ECS Task State Change` events for the
  cluster where `lastStatus == RUNNING`, targeting that Lambda.

This fires on every task replacement, deploy or not, so the DNS record
never goes stale. `deploy.yml` no longer has a Cloudflare step.

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

future troubleshooting:
resolve port redirecction with route 53 (If a migration from Cloudflare to Route 53 applies)
