#!/usr/bin/env bash
# Terraform plans the blast radius and asks for confirmation by default.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
exec terraform -chdir="$ROOT/terraform" destroy "$@"
