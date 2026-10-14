#!/usr/bin/env bash
# Terraform entry point for all infrastructure provisioning.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ACTION="${1:-apply}"
shift || true

case "$ACTION" in
  init|plan|apply)
    exec terraform -chdir="$ROOT/terraform" "$ACTION" "$@"
    ;;
  *)
    echo "Usage: $0 [init|plan|apply] [Terraform options]" >&2
    exit 64
    ;;
esac
