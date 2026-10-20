#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${1:-delivery}"
SERVICE="${2:-shipment-service}"

echo "Scaling $SERVICE to 0 replicas..."
kubectl scale deploy/"$SERVICE" --replicas=0 -n "$NAMESPACE"

echo "Current pods:"
kubectl get pods -n "$NAMESPACE" -l app="$SERVICE"

echo ""
echo "Waiting for Prometheus alert rule to trigger (up == 0, for: 1m)."
read -rp "Press Enter to restore $SERVICE..."

echo "Restoring replicas to 2..."
kubectl scale deploy/"$SERVICE" --replicas=2 -n "$NAMESPACE"

kubectl rollout status deploy/"$SERVICE" -n "$NAMESPACE"
echo "Service restored."
