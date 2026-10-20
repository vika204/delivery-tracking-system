param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "Enabling chaos failure rate (rate=0.8)..."
Invoke-RestMethod -Uri "$BaseUrl/internal/chaos/fail?rate=0.8" -Method Post | ConvertTo-Json

Write-Host "Sending requests to /shipments for 75 seconds..."
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$count = 0

while ($stopwatch.Elapsed.TotalSeconds -lt 75) {
    $count++
    $key = "chaos-$([guid]::NewGuid().ToString())"
    $body = @{
        senderId = 101
        recipientId = 202
        origin = "Kyiv"
        destination = "Lviv"
        weight = 3.5
    } | ConvertTo-Json

    try {
        $res = Invoke-WebRequest -Uri "$BaseUrl/shipments" -Method Post `
            -Headers @{ "Idempotency-Key" = $key } `
            -ContentType "application/json" `
            -Body $body `
            -TimeoutSec 3 `
            -ErrorAction SilentlyContinue
        Write-Host "Request $count: status $($res.StatusCode)"
    } catch {
        Write-Host "Request $count: failed ($($_.Exception.Message))"
    }
    Start-Sleep -Seconds 1
}

Write-Host "`nTraffic completed. Waiting for HighFailedShipments alert."
Read-Host "Press Enter to reset chaos"

Write-Host "Resetting chaos..."
Invoke-RestMethod -Uri "$BaseUrl/internal/chaos" -Method Delete | ConvertTo-Json
Write-Host "Chaos reset."
