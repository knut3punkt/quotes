<#
.SYNOPSIS
    Starts the TV Quotes dev servers: the Ktor server and the admin Vite dev server.

.DESCRIPTION
    Launches the Ktor server (":server:run") and the admin frontend ("npm run dev") each in
    their own PowerShell window, so their logs stay separate and either can be stopped
    independently with Ctrl+C without killing the other.
#>

$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "Starting Ktor server (:server:run) on http://localhost:8080 ..."
Start-Process powershell.exe -ArgumentList @(
    '-NoExit',
    '-Command',
    "`$Host.UI.RawUI.WindowTitle = 'TV Quotes - server'; Set-Location '$repoRoot'; .\gradlew.bat :server:run"
)

Write-Host "Starting admin dev server (npm run dev) on http://localhost:5173 ..."
Start-Process powershell.exe -ArgumentList @(
    '-NoExit',
    '-Command',
    "`$Host.UI.RawUI.WindowTitle = 'TV Quotes - admin'; Set-Location '$repoRoot\admin'; npm run dev"
)

Write-Host "Both dev servers are starting in separate windows. Close each window (or Ctrl+C) to stop it."
