#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

echo "Enabling chaos failure rate (rate=0.8)..."
curl -s -X POST "${BASE_URL}/internal/chaos/fail?rate=0.8"
echo ""

echo "Sending requests to /shipments for 75 seconds..."
START_TIME=$(date +%s)
while [ $(( $(date +%s) - START_TIME )) -lt 75 ]; do
  IDEMPOTENCY_KEY="chaos-$(date +%s%N)-$RANDOM"
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "${BASE_URL}/shipments" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -d '{
      "senderId": 101,
      "recipientId": 202,
      "origin": "Kyiv",
      "destination": "Lviv",
      "weight": 3.5
    }' || true
  sleep 1
done

echo ""
echo "Traffic completed. Waiting for HighFailedShipments alert."
read -rp "Press Enter to reset chaos..."

echo "Resetting chaos..."
curl -s -X DELETE "${BASE_URL}/internal/chaos"
echo ""
echo "Chaos reset."
