# BFlow infrastructure

Terraform is the single source of truth for AWS infrastructure. It replaces
the sequential AWS CLI bootstrap/destroy workflow, whose partial progress and
implicit `outputs.env` state made a full deployment take two to four days.

The implementation deliberately keeps the pre-revenue footprint small:

- Supabase remains the default PostgreSQL provider; RDS is opt-in.
- One public Fargate service remains managed by the application deployment.
- No NAT gateway, ALB, multi-AZ RDS, Container Insights, or always-on extra
  services are introduced.
- CloudWatch logs retain seven days. ECR lifecycle retention and Budget email
  alerts are opt-in after the migration so the first plan does not alter the
  existing production configuration.

## Layout

`terraform/` owns VPC networking, S3, ECR, ECS cluster, IAM, Secrets Manager
containers, OCR messaging, the GitHub OIDC deploy role, budget, optional RDS,
optional Cognito client, and optional DNS-sync Lambda. Secret **values** do not
enter Terraform state: create/update them directly in AWS Secrets Manager.

`deploy.sh` and `destroy.sh` are tiny Terraform entry points, not bootstrap
scripts.

## First migration

1. Rebuild/reopen the devcontainer so `terraform` is available.
2. `terraform/terraform.tfvars` was generated locally from read-only AWS CLI
   inspection. It is ignored by Git; `terraform.tfvars.example` is the
   shareable, credential-free reference. Do not commit the local file.
3. Initialise Terraform:

   ```bash
   ./deploy.sh init
   ```

4. `terraform/imports.local.tf` is generated locally from AWS CLI discovery
   and ignored by Git; it includes account-specific resource IDs and ARNs.
   Run `./deploy.sh plan` to preview imports and any drift. Do not use `apply`
   until the plan is no-op or contains only intentional drift fixes. Applying
   an import-only plan records state locally; it does not change AWS.

5. Review the plan, then apply interactively:

   ```bash
   ./deploy.sh plan
   ./deploy.sh apply
   ```

6. Put the database, Wompi, Firebase, and (if enabled) Cloudflare values into
   the existing Secrets Manager secrets. Terraform owns only the secret
   containers and access policies.

   If DNS sync is enabled, build its Go source before `plan`/`apply`:

   ```bash
   cd infra/dns-sync
   mkdir -p .build
   CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o .build/bootstrap .
   (cd .build && zip -q lambda_function.zip bootstrap)
   ```

7. Copy `terraform output deployment_variables`, `secret_arns`, and
   `github_actions_role_arn` to the GitHub `production` environment.

Use `./destroy.sh` only after reviewing Terraform's destruction plan. S3 has
`force_destroy = false`, so uploaded customer files are never silently deleted.

## CI/CD

Pull requests affecting `infra/` now run `terraform fmt` and `terraform
validate` with no AWS credentials. Application pushes continue to deploy ECS
through the scoped GitHub OIDC role. Infrastructure apply remains a deliberate
operator action until a separately scoped Terraform CI role and remote state
backend are introduced.
