param(
    [string]$BridgeUrl = "http://localhost:8080"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "1. Health check"
Invoke-RestMethod -Uri "$BridgeUrl/health" -Method Get | ConvertTo-Json

Write-Host "2. Ready check"
try {
    Invoke-RestMethod -Uri "$BridgeUrl/ready" -Method Get | ConvertTo-Json
} catch {
    Write-Host "Not ready: check BOT_TOKEN and CHAT_ID"
}

Write-Host "3. Sending firing alert"
$firing = Get-Content -Raw -Path "$scriptDir/payloads/alert-firing.json"
Invoke-RestMethod -Uri "$BridgeUrl/alert" -Method Post -ContentType "application/json" -Body $firing | ConvertTo-Json

Write-Host "4. Sending resolved alert"
$resolved = Get-Content -Raw -Path "$scriptDir/payloads/alert-resolved.json"
Invoke-RestMethod -Uri "$BridgeUrl/alert" -Method Post -ContentType "application/json" -Body $resolved | ConvertTo-Json
