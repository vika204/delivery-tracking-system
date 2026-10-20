#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

echo "Setting latency to 1500ms..."
curl -s -X POST "${BASE_URL}/internal/chaos/latency?ms=1500"
echo ""

echo "Sending requests to GET /shipments for 135 seconds..."
START_TIME=$(date +%s)
while [ $(( $(date +%s) - START_TIME )) -lt 135 ]; do
  curl -s -o /dev/null -w "status: %{http_code}, time: %{time_total}s\n" "${BASE_URL}/shipments" || true
done

echo ""
echo "Traffic completed. Waiting for HighLatency alert."
read -rp "Press Enter to reset latency..."

echo "Resetting chaos..."
curl -s -X DELETE "${BASE_URL}/internal/chaos"
echo ""
echo "Latency reset."
