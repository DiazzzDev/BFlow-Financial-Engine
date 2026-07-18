# ADR-0006: Multi-Stage CI/CD Pipeline with Security Gates, Environment Validation, and Automated ECS Deployment

- **Status:** Accepted
- **Date:** 2026-07-12

## Context

BFlow requires a production deployment pipeline capable of provisioning new
application versions safely while minimizing operational effort.

Because the project is deployed to AWS ECS Fargate through GitHub Actions,
deployment failures can originate from several independent systems:

- Source code quality issues.
- Accidental secret exposure.
- Container vulnerabilities.
- Missing AWS resources.
- Incorrect GitHub repository configuration.
- Invalid IAM permissions.
- Cloudflare configuration drift.
- ECS networking misconfiguration.

Deploying without validating these dependencies would allow failures to occur
late in the pipeline, after images have already been built and pushed or after
partial infrastructure changes have been applied.

The pipeline therefore needed to become progressively stricter, separating
security validation, infrastructure validation, and deployment into distinct
stages with clear responsibilities.

## Decision

The deployment process is implemented as a **multi-stage GitHub Actions
pipeline** composed of independent jobs connected through explicit
dependencies.

The workflow is organized into three logical stages.

### Stage 1 — Security

A reusable workflow performs repository security verification before any
deployment activity.

This stage executes:

- Gitleaks secret scanning across the complete Git history.
- Docker image construction.
- Trivy vulnerability scanning against the locally built image.
- Deployment blocking when HIGH or CRITICAL vulnerabilities are detected,
  except those explicitly documented in `.trivyignore`.

This workflow is reusable through `workflow_call`, allowing future pipelines
to consume the same security policy without duplication.

### Stage 2 — Environment Validation

Before any deployment begins, the pipeline validates every external dependency
required by the application.

Validation includes:

- Required GitHub Secrets.
- Required GitHub Variables.
- IAM role existence.
- AWS OIDC authentication.
- Secrets Manager secret.
- ECR repository.
- ECS cluster.
- ECS networking.
- Public subnet configuration.
- CloudWatch Log Group.
- Cloudflare API token.
- Cloudflare Zone.
- Cloudflare DNS record.
- DNS record type.
- DNS record ownership.

The deployment immediately aborts if any dependency is missing or invalid.

### Stage 3 — Deployment

After all validation succeeds, the deployment stage performs the application
release.

The deployment process:

1. Builds the Docker image.
2. Pushes the image to Amazon ECR.
3. Generates the ECS Task Definition from a version-controlled template.
4. Generates ECS network configuration from templates.
5. Validates generated JSON files.
6. Registers a new ECS Task Definition revision.
7. Creates the ECS Service if it does not already exist.
8. Updates the ECS Service on subsequent deployments.
9. Waits until ECS reports service stability.
10. Resolves the task's public IP.
11. Updates the Cloudflare DNS record automatically.
12. Produces a deployment summary.

The deployment workflow is designed to be idempotent.

If the ECS Service already exists, it is updated.

If the ECS Service does not exist, it is created automatically.

Race conditions caused by concurrent service creation are handled by
attempting an update when AWS reports that the service already exists.

## Consequences

### Positive

- Security verification occurs before deployment.
- Secret leaks are detected automatically.
- Vulnerable container images are prevented from reaching production.
- Infrastructure configuration errors are detected before deployment begins.
- Cloudflare configuration is validated automatically.
- AWS configuration drift is detected early.
- Deployments are deterministic and reproducible.
- Task Definition generation is fully template-driven.
- No manually edited Task Definition JSON is required.
- ECS Service creation is fully automated.
- Subsequent deployments reuse the same deployment path.
- The security workflow is reusable by future repositories.
- Deployment failures provide significantly more diagnostic information than
  the default ECS deployment process.

### Negative

- Pipeline execution time increases due to additional validation stages.
- Additional GitHub variables and secrets must be maintained.
- Deployment depends on both AWS and Cloudflare availability.
- Validation logic introduces additional maintenance overhead as
  infrastructure evolves.

## Implementation Notes

The deployment process is intentionally divided into independent jobs using
GitHub Actions `needs` relationships.

```
Security
      │
      ▼
Environment Validation
      │
      ▼
Deployment
```

The Task Definition committed to the repository is maintained as a template
rather than a concrete deployment artifact.

During deployment, GitHub Actions generates the final ECS Task Definition
using repository variables and secrets through `envsubst`, after which the
resulting JSON is validated with `jq` before registration.

Similarly, ECS network configuration is generated from a template to avoid
hardcoding subnet IDs or security groups.

After ECS reports service stability, the workflow resolves the public IP of
the running task from its attached ENI and synchronizes the corresponding
Cloudflare DNS record, ensuring that traffic always reaches the latest running
task without manual DNS intervention.

This ADR is expected to evolve if the project later adopts blue/green
deployments, Application Load Balancers, multiple ECS services, or
GitHub Environments with progressive deployment approvals.