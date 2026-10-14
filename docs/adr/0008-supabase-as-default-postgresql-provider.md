# ADR-0008: Supabase as the Default Managed PostgreSQL Provider

- **Status:** Accepted
- **Date:** 2026-08-21

## Context

BFlow is a pre-revenue SaaS. Amazon RDS (even a single-AZ `db.t4g.micro`)
carries a fixed monthly cost from the moment it is provisioned, regardless
of traffic. For a project with no paying customers yet, that fixed cost is
not justified while a managed Postgres provider with a usable free tier
(Supabase) can serve the same role during this stage.

At the same time, the project must not lose the ability to move to RDS
later — e.g. once traffic, compliance, or VPC-isolation requirements justify
it — without a rewrite.

The database connection is fully externalized into generic `DB_HOST` /
`DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` values consumed via Spring's
standard `spring.datasource.*` properties, sourced from a single AWS Secrets
Manager secret (`${PROJECT_NAME}/database`). Nothing in the application, the
ECS task definition, or the IAM policy grants is coupled to "RDS" beyond that
secret's contents.

## Decision

Terraform variable `db_provider` selects `supabase` (default) or `rds`.
Terraform creates no RDS resources while the value is `supabase`; switching to
`rds` is an explicit, reviewed infrastructure change. The database secret ARN
remains stable, while its connection value is updated directly in Secrets
Manager. See ADR-0011 for the Terraform migration and state-import process.

Explicitly out of scope for this change: Supabase Auth, Supabase Storage,
and the Supabase client SDK. Supabase is used strictly as managed
PostgreSQL, reached over the standard `postgresql://` JDBC URL. Cognito,
S3, SES, and the ECS/ECR/CloudWatch/IAM/OIDC layers are unaffected.

## Consequences

### Positive

- $0 additional AWS cost for the database layer while pre-revenue (Supabase
  free tier), versus a fixed RDS bill from day one.
- Spring Boot, Flyway, ECS, and IAM remain completely unaware of which
  provider is active — the abstraction ADR-0005 already provided turned out
  to be sufficient, so no new abstraction layer was introduced.
- Moving to RDS is an explicit Terraform plan/apply plus a database-secret
  update, documented in `infra/README.md`. No downtime-inducing
  IAM/ECS/task-definition edits are needed because the secret ARN never
  changes.
- ECS tasks already run in public subnets with `assignPublicIp: ENABLED`
  (for the existing Cloudflare dynamic DNS flow), so outbound connectivity
  to Supabase's public endpoint requires no NAT Gateway, no VPC peering, and
  no security group changes.

### Negative

- Supabase-managed Postgres is outside the VPC, so the network-level
  isolation RDS provided (private subnet, security-group-only ingress) does
  not apply to this provider. This is an accepted trade-off for the
  pre-revenue stage; DB credentials still flow only through Secrets Manager
  and TLS (`sslmode=require`) is enforced on the connection.
- Data migration between the two Postgres instances (`pg_dump`/`pg_restore`
  or logical replication) is a manual step, not automated by this toggle.
- Secret values are deliberately managed outside Terraform state, which means
  an operator must update Secrets Manager after changing providers.

## Implementation Notes

- `infra/terraform/terraform.tfvars.example` documents the cost-safe default.
- The Secrets Manager/GitHub name `RDS_SECRET_ARN` remains unchanged for
  deployment compatibility even when it points to Supabase credentials.
