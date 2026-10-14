# ADR-0011: Terraform as the infrastructure source of truth

**Status:** Accepted

## Context

AWS infrastructure was created through an ordered collection of Bash/AWS CLI
scripts. State was distributed across AWS, local `outputs.env`, and human
knowledge of which scripts had already run. A first deployment could take two
to four days, especially after an interrupted run or an IAM policy drift.

BFlow is pre-revenue. The migration must improve repeatability without adding
always-on infrastructure or moving credentials into source control/state.

## Decision

Terraform under `infra/terraform` is the declarative source of truth. The
active `infra/deploy.sh` and `infra/destroy.sh` delegate to Terraform. The old
infrastructure Bash scripts are removed once the migration is committed so
they cannot overwrite Terraform-managed resources or reintroduce drift.

Terraform owns networking, S3 controls, ECR lifecycle, ECS cluster, IAM/OIDC,
Secrets Manager secret containers, OCR resources, the budget, and optional
RDS/Cognito/DNS-sync resources. ECS task definitions/services remain owned by
the application deploy workflow because each release selects an immutable image
tag.

The default database provider stays `supabase`; `db_provider = "rds"` is an
explicit opt-in. We deliberately omit NAT, ALB, multi-AZ RDS, Container
Insights, and automatic infrastructure apply from application deploys.

Secret values are not Terraform inputs. Terraform creates the secret
containers and grants least-privilege access; operators rotate values directly
through Secrets Manager. This prevents credentials from being persisted in a
local Terraform state file.

## Migration blueprint

1. Rebuild the devcontainer to obtain Terraform.
2. Configure `terraform.tfvars` from the committed example.
3. Initialise Terraform and import existing resources into state.
4. Reconcile `terraform plan` until it contains only intentional changes.
5. Apply interactively, then copy Terraform outputs to GitHub production
   variables/secrets.
6. Stop running the legacy scripts.

No resource is recreated during import. A remote, access-controlled state
backend and separate Terraform CI role are a later step; until then, state is
operator-owned and infrastructure apply requires an administrator review.

## Consequences

Infrastructure planning becomes dependency-aware and reviewable before any AWS
mutation. Terraform's expected execution is minutes rather than the previous
two-to-four-day recovery cycle. CI checks formatting and validation on every
infrastructure pull request.

The trade-off is an explicit one-time import and careful state custody. The
repository no longer carries a second provisioning implementation, preventing
untracked drift and accidental policy overwrites.
