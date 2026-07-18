# ADR-0005: Portable and Idempotent Infrastructure Provisioning with Bash Scripts

- **Status:** Accepted
- **Date:** 2026-07-12

## Context

BFlow required a repeatable way to provision and remove its complete AWS
infrastructure without relying on infrastructure-as-code frameworks such as
Terraform or AWS CDK.

The project is intended to remain easy to understand for developers learning
AWS fundamentals while avoiding external tooling, state files, or additional
dependencies. At the same time, the infrastructure must be reproducible,
portable across AWS accounts, and safe to execute multiple times without
creating duplicate resources.

Several provisioning approaches were evaluated.

- **Terraform** provides mature state management and infrastructure planning,
  but introduces an additional language, backend state management, provider
  versioning, and operational complexity that were not justified for a
  learning-focused project of this size.
- **AWS CDK** offers higher abstraction through general-purpose languages, but
  couples infrastructure provisioning to a specific SDK and deployment model.
- **CloudFormation** was considered unnecessarily verbose for the project's
  current requirements.
- **Custom Bash automation** provides complete visibility into every AWS CLI
  operation while keeping infrastructure provisioning dependency-free beyond
  the AWS CLI itself.

Because the repository is public, portability became a primary design goal.
Scripts must execute successfully in any AWS account after providing only the
required configuration values, without embedding account-specific identifiers
inside the repository.

## Decision

Infrastructure is provisioned through a collection of modular Bash scripts,
executed sequentially by a single orchestration script.

The provisioning process follows these principles:

- Each AWS service or infrastructure component is managed by its own script
  (VPC, networking, security groups, IAM, ECR, RDS, Secrets Manager,
  CloudWatch, ECS, GitHub OIDC, AWS Budgets, etc.).
- Every script is designed to be **idempotent**, checking whether the target
  resource already exists before attempting creation.
- Resources discovered or created during execution are stored in a shared
  `outputs.env` file, allowing subsequent scripts to dynamically consume AWS
  resource identifiers instead of relying on hardcoded values.
- Environment-specific configuration remains isolated in `config.env`, while
  generated outputs are persisted separately.
- Long-lived generated secrets, such as the initial RDS master password, are
  stored locally in `secrets.env` to support future infrastructure recreation
  without requiring manual password regeneration.
- A complementary destroy workflow removes resources in reverse dependency
  order, allowing complete infrastructure teardown while preserving the same
  portability guarantees.

Infrastructure provisioning and application deployment are intentionally
separated.

Bootstrap scripts provision only long-lived AWS infrastructure. Container image
publication, ECS task definition registration, and ECS service deployments are
performed exclusively through GitHub Actions.

## Consequences

### Positive

- Infrastructure can be recreated in any AWS account using only configuration
  files and the AWS CLI.
- Scripts may be executed repeatedly without producing duplicate resources or
  requiring manual cleanup.
- AWS resource identifiers remain dynamically discoverable instead of being
  committed into source control.
- Infrastructure provisioning and application deployment remain cleanly
  separated, reducing coupling between bootstrap operations and release
  automation.
- Modular scripts simplify maintenance by isolating each AWS service into an
  independent provisioning unit.
- Destroy scripts mirror provisioning order, reducing orphaned AWS resources
  and making environment cleanup predictable.
- The repository remains portable because it contains no account-specific ARNs,
  resource IDs, or generated identifiers.

### Negative

- Unlike Terraform or CloudFormation, Bash provides no execution plan or
  automatic state reconciliation.
- Idempotency must be maintained manually whenever new AWS resources are added.
- Dependency management remains the responsibility of the provisioning scripts.
- Partial failures may require rerunning provisioning scripts to complete
  resource creation.

## Implementation Notes

Infrastructure provisioning is organized into sequential scripts, each
responsible for a single AWS service or logical infrastructure component.

Common helper functions encapsulate shared behavior, including:

- validating required outputs before execution;
- persisting generated resource identifiers;
- updating existing outputs without creating duplicates;
- simplifying cross-script communication.

The provisioning process uses three configuration layers:

- `config.env` for user-defined infrastructure configuration.
- `outputs.env` for dynamically generated AWS resource identifiers.
- `secrets.env` for locally generated secrets that cannot be recovered from AWS.

No AWS account IDs, ARNs, resource identifiers, or deployment-specific values
are committed into the repository. These values are resolved dynamically during
provisioning and exported only for subsequent scripts.

ECS task definitions are intentionally excluded from infrastructure bootstrap.
Infrastructure scripts provision only the ECS cluster, networking, IAM roles,
CloudWatch log groups, and supporting services.

GitHub Actions is responsible for:

- building and publishing immutable Docker images to Amazon ECR;
- rendering the ECS task definition template with the new image;
- registering a new ECS task definition revision;
- updating the ECS service;
- waiting for service stabilization before completing deployment.

This separation keeps infrastructure stable while allowing deployments to
remain fully immutable and independent from infrastructure provisioning,
improving portability, maintainability, and long-term scalability.