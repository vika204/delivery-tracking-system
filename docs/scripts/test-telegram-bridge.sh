#!/usr/bin/env bash
set -euo pipefail

BRIDGE_URL="${1:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "1. Health check"
curl -s -i "${BRIDGE_URL}/health"
echo ""

echo "2. Ready check"
curl -s -i "${BRIDGE_URL}/ready"
echo ""

echo "3. Sending firing alert"
curl -s -i -X POST "${BRIDGE_URL}/alert" \
  -H "Content-Type: application/json" \
  --data-binary @"${SCRIPT_DIR}/payloads/alert-firing.json"
echo ""

echo "4. Sending resolved alert"
curl -s -i -X POST "${BRIDGE_URL}/alert" \
  -H "Content-Type: application/json" \
  --data-binary @"${SCRIPT_DIR}/payloads/alert-resolved.json"
echo ""
