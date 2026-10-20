param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "Setting latency to 1500ms..."
Invoke-RestMethod -Uri "$BaseUrl/internal/chaos/latency?ms=1500" -Method Post | ConvertTo-Json

Write-Host "Sending requests to GET /shipments for 135 seconds (rule for: 2m)..."
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$count = 0

while ($stopwatch.Elapsed.TotalSeconds -lt 135) {
    $count++
    $reqWatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $res = Invoke-WebRequest -Uri "$BaseUrl/shipments" -Method Get -TimeoutSec 5 -ErrorAction SilentlyContinue
        $reqWatch.Stop()
        Write-Host "Request $count: $($res.StatusCode) in $([int]$reqWatch.Elapsed.TotalMilliseconds)ms"
    } catch {
        $reqWatch.Stop()
        Write-Host "Request $count: timeout / error"
    }
}

Write-Host "`nTraffic completed. Waiting for HighLatency alert."
Read-Host "Press Enter to reset latency"

Write-Host "Resetting chaos..."
Invoke-RestMethod -Uri "$BaseUrl/internal/chaos" -Method Delete | ConvertTo-Json
Write-Host "Latency reset."
