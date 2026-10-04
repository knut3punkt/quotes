<#
.SYNOPSIS
    Builds the tv-web app, packages it for webOS and installs and launches it on a TV.

.DESCRIPTION
    Runs "npm run build" in platforms/tv-web, packages dist/ into an .ipk with ares-package,
    installs it on the given ares device (replacing the installed version) and launches it.
    Requires LG's ares-cli on PATH and the device registered with ares-setup-device.

    VITE_API_BASE_URL is baked in at build time from platforms/tv-web/.env.production.local,
    so that file must point at the dev machine's LAN IP (see platforms/tv-web/README.md).

.PARAMETER Device
    The ares device name to install on. Defaults to "mammas".

.EXAMPLE
    .\scripts\deploy-webos.ps1
    .\scripts\deploy-webos.ps1 -Device emulator
#>
param(
    [string]$Device = 'mammas'
)

$ErrorActionPreference = 'Stop'

$tvWebDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'platforms\tv-web'
$packageDir = Join-Path $tvWebDir 'dist-ipk'

function Invoke-Step([string]$description, [scriptblock]$command) {
    Write-Host "==> $description" -ForegroundColor Cyan
    & $command
    if ($LASTEXITCODE -ne 0) {
        throw "$description failed (exit code $LASTEXITCODE)."
    }
}

if (-not (Test-Path (Join-Path $tvWebDir '.env.production.local'))) {
    Write-Warning ".env.production.local is missing, so the build will not know the server's LAN address. See platforms/tv-web/README.md."
}

$appId = (Get-Content (Join-Path $tvWebDir 'public\appinfo.json') -Raw | ConvertFrom-Json).id

Push-Location $tvWebDir
try {
    Invoke-Step 'Building tv-web' { npm run build }

    # Start from an empty package directory, so the .ipk installed below is the one just built.
    if (Test-Path $packageDir) {
        Remove-Item -Recurse -Force $packageDir
    }
    Invoke-Step 'Packaging for webOS' { ares-package dist -o $packageDir }
    $ipk = Get-ChildItem $packageDir -Filter '*.ipk' | Select-Object -First 1

    Invoke-Step "Installing $($ipk.Name) on $Device" { ares-install --device $Device $ipk.FullName }
    Invoke-Step "Launching $appId on $Device" { ares-launch --device $Device $appId }
} finally {
    Pop-Location
}

Write-Host "Done: $appId is updated and running on $Device." -ForegroundColor Green
