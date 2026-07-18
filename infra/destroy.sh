#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Destroying infrastructure..."

for script in \
    "$ROOT/destroy/13-budget.sh" \
    "$ROOT/destroy/12-github-oidc.sh" \
    "$ROOT/destroy/11-ecs.sh" \
    "$ROOT/destroy/10-cloudwatch.sh" \
    "$ROOT/destroy/09-iam.sh" \
    "$ROOT/destroy/08-secrets.sh" \
    "$ROOT/destroy/07-rds.sh" \
    "$ROOT/destroy/06-ecr.sh" \
    "$ROOT/destroy/05-security-groups.sh" \
    "$ROOT/destroy/04-route-tables.sh" \
    "$ROOT/destroy/03-internet-gateway.sh" \
    "$ROOT/destroy/02-subnets.sh" \
    "$ROOT/destroy/01-vpc.sh"
do
    echo
    echo "=================================================="
    echo "Running $(basename "$script")"
    echo "=================================================="

    bash "$script"
done

echo
echo "Infrastructure destroyed."