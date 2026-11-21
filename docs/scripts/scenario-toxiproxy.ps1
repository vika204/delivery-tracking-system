param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$ShipmentId,
    [string]$TrackingUrl = 'http://localhost:18084',
    [string]$ToxiproxyUrl = 'http://localhost:18474',
    [ValidateRange(0, 60)]
    [int]$ObserveSeconds = 15
)

$ErrorActionPreference = 'Stop'
$TrackingUrl = $TrackingUrl.TrimEnd('/')
$ToxiproxyUrl = $ToxiproxyUrl.TrimEnd('/')
$proxyUrl = "$ToxiproxyUrl/proxies/shipment"
$proxyRequestOptions = @{TimeoutSec = 10; UserAgent = 'WEB-EXPRESS-Toxiproxy'}
$shipmentRequest = @{Uri = "$TrackingUrl/tracking/shipments/$ShipmentId"; TimeoutSec = 30}
$latencyAdded = $false

function Get-BreakerState {
    $result = Invoke-RestMethod -Uri "$TrackingUrl/actuator/circuitbreakers" -TimeoutSec 10
    return $result.circuitBreakers.shipmentClient.state
}

function Get-ShipmentAttempts {
    $metrics = (Invoke-WebRequest -Uri "$TrackingUrl/actuator/prometheus" -UseBasicParsing -TimeoutSec 10).Content
    $match = [regex]::Match($metrics,
        '(?m)^delivery_tracking_shipment_client_seconds_count\{[^\r\n}]*operation="get"[^\r\n}]*\}\s+([0-9.eE+-]+)\s*$')
    if (!$match.Success) {
        throw 'Shipment client metric is missing. Use the current tracking-service image.'
    }
    return [double]::Parse($match.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)
}

$proxy = Invoke-RestMethod -Uri $proxyUrl @proxyRequestOptions
if (!$proxy.enabled -or @($proxy.toxics).Count -ne 0) {
    throw 'The shipment proxy must be enabled and have no toxics before the test.'
}

try {
    Write-Host '1. Healthy connection' -ForegroundColor Cyan
    $shipment = Invoke-RestMethod @shipmentRequest
    if ($shipment.shipmentId -ne $ShipmentId -or $shipment.status -eq 'UNAVAILABLE' -or (Get-BreakerState) -ne 'CLOSED') {
        throw 'Expected an existing shipment and CLOSED breaker. Check the shipment ID and restart the demo tracking pod.'
    }
    Write-Host "Shipment #$ShipmentId status=$($shipment.status), breaker=CLOSED"
    Start-Sleep -Seconds $ObserveSeconds

    Write-Host '2. Response latency: 5000 ms, above the 3000 ms read timeout' -ForegroundColor Cyan
    $attemptsBefore = Get-ShipmentAttempts
    $toxic = @{name = 'demo-latency'; type = 'latency'; stream = 'downstream'; toxicity = 1.0; attributes = @{latency = 5000; jitter = 0}}
    Invoke-RestMethod -Method Post -Uri "$proxyUrl/toxics" -ContentType 'application/json' -Body ($toxic | ConvertTo-Json) @proxyRequestOptions | Out-Null
    $latencyAdded = $true
    $elapsed = [System.Diagnostics.Stopwatch]::StartNew()
    $shipment = Invoke-RestMethod @shipmentRequest
    $elapsed.Stop()
    $attempts = (Get-ShipmentAttempts) - $attemptsBefore
    if ($shipment.status -ne 'UNAVAILABLE' -or $attempts -ne 3) {
        throw "Expected UNAVAILABLE after 3 attempts; got status=$($shipment.status), attempts=$attempts. Check that tracking uses Toxiproxy and no other requests interfere."
    }
    Invoke-RestMethod -Method Delete -Uri "$proxyUrl/toxics/demo-latency" @proxyRequestOptions | Out-Null
    $latencyAdded = $false
    Write-Host "Fallback=UNAVAILABLE, attempts=$attempts, elapsed=$([math]::Round($elapsed.Elapsed.TotalSeconds, 2)) s"
    Start-Sleep -Seconds $ObserveSeconds

    Write-Host '3. Connection failure and Circuit Breaker OPEN' -ForegroundColor Cyan
    Invoke-RestMethod -Method Post -Uri $proxyUrl -ContentType 'application/json' -Body '{"enabled":false}' @proxyRequestOptions | Out-Null
    $state = Get-BreakerState
    for ($request = 1; $request -le 10 -and $state -ne 'OPEN'; $request++) {
        $shipment = Invoke-RestMethod @shipmentRequest
        if ($shipment.status -ne 'UNAVAILABLE') {
            throw 'A disabled proxy must produce UNAVAILABLE. Check SHIPMENT_SERVICE_URL.'
        }
        $state = Get-BreakerState
        Write-Host "Failed request ${request}: breaker=$state"
    }
    if ($state -ne 'OPEN') {
        throw 'Circuit Breaker did not open after repeated failures.'
    }
    $attemptsBefore = Get-ShipmentAttempts
    $shipment = Invoke-RestMethod @shipmentRequest
    if ($shipment.status -ne 'UNAVAILABLE' -or (Get-ShipmentAttempts) -ne $attemptsBefore) {
        throw 'An OPEN breaker must return fallback without calling shipment-service.'
    }
    Write-Host 'OPEN confirmed: fallback returned with zero upstream attempts.'
    Start-Sleep -Seconds $ObserveSeconds

    Write-Host '4. Restore the connection and verify recovery' -ForegroundColor Cyan
    Invoke-RestMethod -Method Post -Uri $proxyUrl -ContentType 'application/json' -Body '{"enabled":true}' @proxyRequestOptions | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        $shipment = Invoke-RestMethod @shipmentRequest
        $state = Get-BreakerState
        Write-Host "Recovery: status=$($shipment.status), breaker=$state"
        if ($state -eq 'CLOSED' -and $shipment.shipmentId -eq $ShipmentId -and $shipment.status -ne 'UNAVAILABLE') {
            break
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($state -ne 'CLOSED' -or $shipment.shipmentId -ne $ShipmentId -or $shipment.status -eq 'UNAVAILABLE') {
        throw 'The service did not recover within 45 seconds.'
    }
    Write-Host 'PASS: timeout, 3 retry attempts, fallback, OPEN, and recovery to CLOSED.' -ForegroundColor Green
    Start-Sleep -Seconds $ObserveSeconds
} finally {
    try {
        if ($latencyAdded) {
            Invoke-RestMethod -Method Delete -Uri "$proxyUrl/toxics/demo-latency" @proxyRequestOptions | Out-Null
        }
    } finally {
        Invoke-RestMethod -Method Post -Uri $proxyUrl -ContentType 'application/json' -Body '{"enabled":true}' @proxyRequestOptions | Out-Null
    }
}
