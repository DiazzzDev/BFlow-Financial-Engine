# Open source readiness roadmap

This roadmap prioritizes making BFlow safe and predictable to run for someone
who does not have access to the maintainers' AWS account. It is a planning
document; each item should become a focused issue before implementation.

## 1. First local run

- Make the root Docker Compose path the standard local setup and document the
  exact clone, configure, start, health-check, and stop commands for Windows,
  macOS, and Linux.
- Keep local defaults self-contained: PostgreSQL in Compose, mock or disabled
  cloud integrations, and no production endpoints or credentials required.
- Define one source of truth for environment variables and generate `.env.example`
  from the app's supported configuration, marking required, optional, and
  cloud-only settings.
- Verify a clean checkout can start without the devcontainer; retain the
  devcontainer as an optional preconfigured environment.

## 2. Safe, portable infrastructure

- Replace account-specific values in `infra/terraform/terraform.tfvars.example`
  with clearly named placeholders and document the minimum AWS permissions,
  expected costs, and resources created.
- Make environment selection explicit and safe. Keep production resource names
  out of defaults used by new contributors.
- Document state setup, backup, locking, import, and recovery. The current
  Terraform configuration uses operator-local state, which is unsuitable for
  shared infrastructure changes without an agreed remote backend.
- Separate local application setup from AWS deployment; cloud deployment
  should remain an opt-in operator workflow with a reviewed plan.

## 3. Contributor workflow and community

- Publish a short contribution path covering Java version, formatting,
  focused tests, pull request expectations, and how to report security issues.
- Add a maintained feature and technical-debt roadmap with issue links,
  milestones, and clear acceptance criteria. Keep speculative features out of
  the install guide.
- Add a release checklist for migrations, API compatibility, changelog updates,
  and deploy rollback expectations.

## Suggested order

1. Local first-run experience and environment-variable documentation.
2. Safe generic Terraform examples and explicit cost/permission guidance.
3. Shared Terraform state and infrastructure change controls, when more than
   one maintainer needs to operate the same AWS environment.
4. Contributor, security-reporting, and release processes.
