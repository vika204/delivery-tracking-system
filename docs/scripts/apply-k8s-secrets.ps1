param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '../../.env'),
    [string]$Context = 'minikube',
    [switch]$IncludeTelegram,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $values[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}

$secretData = @{}
foreach ($moduleName in @('auth', 'shipment', 'dispatch', 'tracking', 'notification')) {
    $prefix = $moduleName.ToUpperInvariant()
    $secretData["$moduleName-db-secret"] = @{
        POSTGRES_USER = $values["${prefix}_DB_USER"]
        POSTGRES_PASSWORD = $values["${prefix}_DB_PASSWORD"]
    }
}
$secretData['shipment-geoapify-secret'] = @{
    GEOAPIFY_API_KEY = $values['GEOAPIFY_API_KEY']
}
if ($IncludeTelegram) {
    $secretData['alertmanager-telegram'] = @{
        BOT_TOKEN = $values['BOT_TOKEN']
        CHAT_ID = $values['CHAT_ID']
    }
}

foreach ($name in $secretData.Keys) {
    foreach ($key in $secretData[$name].Keys) {
        if ([string]::IsNullOrWhiteSpace($secretData[$name][$key])) {
            throw "Missing value for $name / $key in $EnvFile"
        }
    }
}

if ($ValidateOnly) {
    Write-Host "Ready to create secrets: $(($secretData.Keys | Sort-Object) -join ', ')"
    return
}

$items = foreach ($name in $secretData.Keys) {
    @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{name = $name; namespace = 'delivery'}
        type = 'Opaque'
        stringData = $secretData[$name]
    }
}
@{apiVersion = 'v1'; kind = 'List'; items = @($items)} |
    ConvertTo-Json -Depth 6 |
    kubectl --context=$Context -n delivery apply -f -
if ($LASTEXITCODE -ne 0) {
    throw 'Could not apply Kubernetes secrets.'
}
