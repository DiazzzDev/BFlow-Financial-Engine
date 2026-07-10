#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Starting infrastructure bootstrap..."

for script in \
    "$ROOT/bootstrap/01-vpc.sh" \
    "$ROOT/bootstrap/02-subnets.sh" \
    "$ROOT/bootstrap/03-internet-gateway.sh" \
    "$ROOT/bootstrap/04-route-tables.sh" \
    "$ROOT/bootstrap/05-security-groups.sh" \
    "$ROOT/bootstrap/06-ecr.sh" \
    "$ROOT/bootstrap/07-rds.sh" \
    "$ROOT/bootstrap/08-secrets.sh" \
    "$ROOT/bootstrap/09-iam.sh" \
    "$ROOT/bootstrap/10-cloudwatch.sh" \
    "$ROOT/bootstrap/11-ecs.sh" \
    "$ROOT/bootstrap/12-github-oidc.sh" \
    "$ROOT/bootstrap/13-budget.sh"
do
    echo
    echo "=================================================="
    echo "Running $(basename "$script")"
    echo "=================================================="

    bash "$script"
done

echo
echo "Infrastructure ready."