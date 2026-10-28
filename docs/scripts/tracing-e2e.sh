#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

rand_hex() {
  od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
}

PAYLOAD='{
  "userId": 42,
  "recipientName": "Taras Shevchenko",
  "recipientPhone": "+380501234567",
  "pickupAddress": "Kyiv, Skovorody 2",
  "deliveryAddress": "Lviv, Rynok 1",
  "weight": 2.5,
  "length": 30.0,
  "width": 20.0,
  "height": 15.0,
  "distance": 540.0,
  "price": 185.00,
  "status": "WAITING_FOR_COURIER"
}'

TRACE_ID_1="$(rand_hex 16)"
TRACEPARENT_1="00-${TRACE_ID_1}-$(rand_hex 8)-01"

echo "[1/2] Success scenario TraceId: ${TRACE_ID_1}"
curl -sS -X POST "${BASE_URL}/tracking/shipments" \
  -H "Content-Type: application/json" \
  -H "traceparent: ${TRACEPARENT_1}" \
  -H "Idempotency-Key: e2e-ok-$(date +%s%N)" \
  -d "${PAYLOAD}"
echo ""

sleep 7
curl -sS "${BASE_URL}/notifications"
echo ""

TRACE_ID_2="$(rand_hex 16)"
TRACEPARENT_2="00-${TRACE_ID_2}-$(rand_hex 8)-01"

echo "[2/2] Error scenario TraceId: ${TRACE_ID_2}"
curl -sS -X POST "${BASE_URL}/internal/chaos/fail?rate=1.0" >/dev/null
curl -sS -X POST "${BASE_URL}/tracking/shipments" \
  -H "Content-Type: application/json" \
  -H "traceparent: ${TRACEPARENT_2}" \
  -H "Idempotency-Key: e2e-err-$(date +%s%N)" \
  -d "${PAYLOAD}" || true
echo ""
curl -sS -X DELETE "${BASE_URL}/internal/chaos" >/dev/null
