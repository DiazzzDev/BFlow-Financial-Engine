# ADR-0006: ECS Fargate Deployment with Public Subnets and Cloudflare-Managed Dynamic DNS

- **Status:** Accepted
- **Date:** 2026-07-12

## Context

BFlow's backend needed a production deployment path on AWS. The project has no
revenue and no active users yet, so infrastructure decisions were optimized
for minimal fixed cost while preserving a clear upgrade path once traffic
justifies additional spend.

Infrastructure is provisioned through custom bash bootstrap scripts (no
Terraform/CDK), building a VPC with public and private subnets, an Internet
Gateway, route tables, and security groups. The initial design reserved
private subnets for a future Application Load Balancer (ALB) and NAT Gateway,
but deferred both.

The following architectural questions were evaluated during this work:

- **NAT Gateway vs. ALB cost**: both carry a fixed hourly charge regardless of
  usage (NAT ~$32/month, ALB ~$16/month) with negligible data-processing cost
  at zero traffic. Neither was justified pre-revenue.
- **How to expose a single Fargate task without an ALB**: Fargate tasks in
  public subnets receive a public IP directly, but that IP is not stable — it
  changes on every service redeployment.
- **How to route Cloudflare-proxied traffic to a non-standard application
  port**: Spring Boot listens on port 8080, but Cloudflare's default proxy
  behavior for a Proxied A record assumes port 80/443 on the origin.

Two managed alternatives to the current design were considered and rejected
for this stage:

- **NAT Gateway + private subnet**: unnecessary fixed cost; the task does not
  need to reach out through a private network for anything not already
  reachable from a public subnet.
- **Application/Network Load Balancer**: unnecessary fixed cost for a single
  task with no horizontal scaling requirement yet.

## Decision

The project deploys the BFlow backend as a **single ECS Fargate task in
public subnets**, with **Cloudflare acting as the sole entry point** and
**GitHub Actions synchronizing the task's public IP into a Cloudflare DNS
record on every deployment**.

Specifically:

- Fargate tasks run in public subnets with `assignPublicIp: ENABLED`. No NAT
  Gateway and no Load Balancer are provisioned at this stage.
- The ECS security group only allows inbound traffic on the application port
  from **Cloudflare's published IP ranges**, refreshed by a bootstrap script
  (`authorize_cloudflare_ranges`). Direct access from arbitrary IPs is denied
  by default; developer access for debugging is granted temporarily and
  explicitly revoked afterward.
- Cloudflare's DNS record for the API subdomain is set to **Proxied**, with
  SSL/TLS mode **Flexible** (Cloudflare↔visitor over HTTPS, Cloudflare↔origin
  over plain HTTP), since the origin does not terminate TLS.
- A Cloudflare **Origin Rule** rewrites the destination port to 8080 for
  requests to the API hostname, since the origin does not listen on the
  proxy's default ports.
- The GitHub Actions deploy workflow, after confirming service stability via
  `ecs wait services-stable`, resolves the new task's ENI and public IP (with
  retry, since IP association can lag behind service stability) and updates
  the corresponding Cloudflare A record via the Cloudflare API. A dedicated
  validation step in the `validate-environment` job confirms the Cloudflare
  API token, zone, and DNS record are valid and correctly scoped before any
  deployment proceeds.
- Because the repository is public, workflow steps deliberately avoid
  printing full API responses or ARNs that would leak the AWS account ID or
  Cloudflare account metadata into public Actions logs; only success/failure
  status and the minimal error message are surfaced.

## Consequences

### Positive

- Zero fixed monthly cost from NAT Gateway or Load Balancer at a stage with
  no revenue or user traffic.
- The origin is never directly reachable except through Cloudflare, since
  the security group only trusts Cloudflare's IP ranges — this is a
  meaningful security boundary, not just a cost optimization.
- DNS stays in sync automatically on every deploy; no manual IP lookup or
  DNS edit is required going forward.
- The deployment pipeline now validates external (Cloudflare) dependencies
  with the same rigor as AWS dependencies, catching misconfiguration before
  a deploy is attempted rather than after.
- The path to introduce an ALB or NAT Gateway later is unobstructed — private
  subnets and route tables already exist and were left in place for that
  purpose.

### Negative

- A single Fargate task is a single point of failure with no load
  balancing; this is an accepted tradeoff until real traffic exists.
- The architecture depends on Cloudflare's proxy and Origin Rules feature
  remaining correctly configured; misconfiguration here (SSL mode, origin
  port, IP range staleness) manifests as opaque 522 errors that are harder
  to diagnose than a standard AWS-only failure.
- Flexible SSL/TLS mode means traffic between Cloudflare and the origin is
  unencrypted; this is acceptable at the current trust boundary (security
  group restricted to Cloudflare ranges) but is a gap that should be revisited
  if the origin ever needs to handle more sensitive traffic patterns or if a
  second hop is introduced.
- Task IP resolution in the pipeline depends on a retry loop rather than a
  guaranteed signal, since ECS service stability and ENI public IP
  association are not strictly synchronized.

## Implementation Notes

The GitHub Actions `deploy` job was extended with two new steps after
`Wait for ECS stability`: `Get new task public IP` (with a bounded retry
loop) and `Update Cloudflare DNS record`. The `validate-environment` job was
extended with a `Validate Cloudflare configuration` step that verifies the
API token, zone, and DNS record independently via the Cloudflare API,
following the same `required[]` array and `::error::` failure convention
already used for AWS configuration checks.

The `bflow-github-actions-role` IAM role required an additional
`ec2:DescribeNetworkInterfaces` permission (unscoped, as this action does not
support resource-level restriction) to resolve the task's public IP from its
ENI.

This decision is expected to be superseded by a future ADR once the project
introduces horizontal scaling or revenue-justified uptime requirements, at
which point the existing private subnets would host an ALB and/or NAT
Gateway.