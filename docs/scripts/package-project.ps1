$rootDir = (Get-Item -Path "$PSScriptRoot/../..").FullName
$archiveName = "delivery-tracking-system-monitoring.zip"
$outputPath = Join-Path $rootDir $archiveName

if (Test-Path $outputPath) {
    Remove-Item $outputPath -Force
}

$tempStaging = Join-Path $env:TEMP "delivery-staging-$([guid]::NewGuid().ToString().Substring(0,8))"
New-Item -ItemType Directory -Path $tempStaging | Out-Null

try {
    $excludePatterns = @(
        '^\.git',
        '^\.idea',
        '\\target(\\.*)?$',
        '\.class$',
        '__pycache__',
        '\.pyc$',
        '\.DS_Store$',
        'Thumbs\.db$',
        '^\.env$',
        'secret\.yml$',
        '\.log$',
        'delivery-tracking-system-monitoring\.zip$'
    )

    Get-ChildItem -Path $rootDir -Recurse | ForEach-Object {
        $relPath = $_.FullName.Substring($rootDir.Length + 1)
        
        $skip = $false
        foreach ($pattern in $excludePatterns) {
            if ($relPath -match $pattern) {
                $skip = $true
                break
            }
        }

        if (-not $skip) {
            $destPath = Join-Path $tempStaging $relPath
            if ($_.PSIsContainer) {
                if (-not (Test-Path $destPath)) {
                    New-Item -ItemType Directory -Path $destPath | Out-Null
                }
            } else {
                $destParent = Split-Path -Parent $destPath
                if (-not (Test-Path $destParent)) {
                    New-Item -ItemType Directory -Path $destParent | Out-Null
                }
                Copy-Item -Path $_.FullName -Destination $destPath -Force
            }
        }
    }

    Compress-Archive -Path "$tempStaging\*" -DestinationPath $outputPath -Force
    Write-Host "Archive created: $outputPath"
    Get-Item $outputPath | Select-Object Name, Length
} finally {
    if (Test-Path $tempStaging) {
        Remove-Item -Path $tempStaging -Recurse -Force -ErrorAction SilentlyContinue
    }
}
