#!/usr/bin/env bash
# Creates the Kubernetes Secrets of the delivery namespace from the local .env (bash version of apply-k8s-secrets.ps1).
# Values never appear on the command line or in files: each Secret is rendered by kubectl from an env file that
# exists only as a pipe.
#
#   docs/scripts/apply-k8s-secrets.sh --context delivery-dev --validate-only
#   docs/scripts/apply-k8s-secrets.sh --context delivery-dev [--include-telegram] [--env-file path/to/.env]

set -euo pipefail

ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.env"
CONTEXT="minikube"
INCLUDE_TELEGRAM=false
VALIDATE_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) CONTEXT="$2"; shift ;;
    --env-file) ENV_FILE="$2"; shift ;;
    --include-telegram) INCLUDE_TELEGRAM=true ;;
    --validate-only) VALIDATE_ONLY=true ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ -f "$ENV_FILE" ]] || { echo "No env file: $ENV_FILE (copy .env.example to .env first)" >&2; exit 1; }

value() { # value <KEY>: reads KEY=VALUE from the env file without executing it
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE" | tail -n 1 | sed -E 's/^[^=]*=//; s/^[[:space:]]+|[[:space:]]+$//g; s/^"(.*)"$/\1/; s/^'"'"'(.*)'"'"'$/\1/'
}

SECRETS=()     # "secret-name|KEY=ENV_NAME KEY=ENV_NAME"
for module in auth shipment dispatch tracking notification; do
  prefix="$(tr '[:lower:]' '[:upper:]' <<<"$module")"
  SECRETS+=("$module-db-secret|POSTGRES_USER=${prefix}_DB_USER POSTGRES_PASSWORD=${prefix}_DB_PASSWORD")
done
SECRETS+=("shipment-geoapify-secret|GEOAPIFY_API_KEY=GEOAPIFY_API_KEY")
$INCLUDE_TELEGRAM && SECRETS+=("alertmanager-telegram|BOT_TOKEN=BOT_TOKEN CHAT_ID=CHAT_ID")

missing=()
for entry in "${SECRETS[@]}"; do
  for pair in ${entry#*|}; do
    [[ -n "$(value "${pair#*=}")" ]] || missing+=("${entry%%|*} / ${pair%%=*} (${pair#*=})")
  done
done
if (( ${#missing[@]} )); then
  printf 'Missing value in %s for:\n' "$ENV_FILE" >&2
  printf '  %s\n' "${missing[@]}" >&2
  exit 1
fi

if $VALIDATE_ONLY; then
  printf 'Ready to create secrets:'; for entry in "${SECRETS[@]}"; do printf ' %s' "${entry%%|*}"; done; echo
  exit 0
fi

for entry in "${SECRETS[@]}"; do
  name="${entry%%|*}"
  kubectl --context="$CONTEXT" -n delivery create secret generic "$name" \
      --from-env-file=<(for pair in ${entry#*|}; do printf '%s=%s\n' "${pair%%=*}" "$(value "${pair#*=}")"; done) \
      --dry-run=client -o yaml \
    | kubectl --context="$CONTEXT" -n delivery apply -f -
done
