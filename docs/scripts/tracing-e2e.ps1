param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function New-TraceHex([int]$bytes) {
    $buf = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
    return ($buf | ForEach-Object { $_.ToString("x2") }) -join ""
}

$payload = @{
    userId          = 42
    recipientName   = "Тарас Шевченко"
    recipientPhone  = "+380501234567"
    pickupAddress   = "Київ, вул. Сковороди, 2"
    deliveryAddress = "Львів, пл. Ринок, 1"
    weight          = 2.5
    length          = 30.0
    width           = 20.0
    height          = 15.0
    distance        = 540.0
    price           = 185.00
    status          = "WAITING_FOR_COURIER"
} | ConvertTo-Json

$traceId1 = New-TraceHex 16
$parentId1 = New-TraceHex 8
$traceparent1 = "00-$traceId1-$parentId1-01"
$idempKey1 = "e2e-ok-$([guid]::NewGuid())"

Write-Host "`n[1/2] Success scenario TraceId: $traceId1" -ForegroundColor Green

$created = Invoke-RestMethod -Uri "$BaseUrl/tracking/shipments" -Method Post `
    -Headers @{
        "traceparent"     = $traceparent1
        "Idempotency-Key" = $idempKey1
    } `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))

$created | ConvertTo-Json
Start-Sleep -Seconds 7

$notifications = Invoke-RestMethod -Uri "$BaseUrl/notifications" -Method Get
$notifications | Where-Object { $_.shipmentId -eq $created.shipmentId } | ConvertTo-Json

$traceId2 = New-TraceHex 16
$parentId2 = New-TraceHex 8
$traceparent2 = "00-$traceId2-$parentId2-01"
$idempKey2 = "e2e-err-$([guid]::NewGuid())"

Write-Host "`n[2/2] Error scenario TraceId: $traceId2" -ForegroundColor Red

Invoke-RestMethod -Uri "$BaseUrl/internal/chaos/fail?rate=1.0" -Method Post | Out-Null
try {
    Invoke-WebRequest -Uri "$BaseUrl/tracking/shipments" -Method Post `
        -Headers @{
            "traceparent"     = $traceparent2
            "Idempotency-Key" = $idempKey2
        } `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) | Out-Null
} catch {
    Write-Host "Expected error: $($_.Exception.Message)" -ForegroundColor Yellow
} finally {
    Invoke-RestMethod -Uri "$BaseUrl/internal/chaos" -Method Delete | Out-Null
}
