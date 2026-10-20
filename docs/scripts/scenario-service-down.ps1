param(
    [string]$Namespace = "delivery",
    [string]$Service = "shipment-service"
)

Write-Host "Scaling $Service to 0 replicas..."
kubectl scale deploy/$Service --replicas=0 -n $Namespace

Write-Host "Current pods:"
kubectl get pods -n $Namespace -l app=$Service

Write-Host "`nWaiting for Prometheus rule to trigger (up == 0, for: 1m)."
Read-Host "Press Enter to restore $Service"

Write-Host "Restoring replicas to 2..."
kubectl scale deploy/$Service --replicas=2 -n $Namespace

kubectl rollout status deploy/$Service -n $Namespace
Write-Host "Service restored."
