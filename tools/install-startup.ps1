<#
.SYNOPSIS
  Installs Notifling for autostart: copies notifling.exe to %APPDATA%\Notifling,
  creates the Startup folder .lnk (runs at login, --minimized) and a Start Menu
  shortcut (required for Windows toast notifications), plus a UDP firewall rule.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\install-startup.ps1
  powershell -ExecutionPolicy Bypass -File tools\install-startup.ps1 -ExePath .\windows\notifling.exe
#>
param(
    [string]$ExePath = "",
    [int]$Port = 51234,
    [switch]$NoFirewall
)

$ErrorActionPreference = "Stop"
$destDir = Join-Path $env:APPDATA "Notifling"
New-Item -ItemType Directory -Path $destDir -Force | Out-Null

if ([string]::IsNullOrWhiteSpace($ExePath)) {
    $candidates = @(
        (Join-Path $PSScriptRoot "..\windows\notifling.exe"),
        (Join-Path (Get-Location) "windows\notifling.exe")
    )
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { $ExePath = $c; break }
    }
}
if (-not (Test-Path -LiteralPath $ExePath)) {
    throw "notifling.exe not found. Build it first (cd windows; go build -o notifling.exe .) or pass -ExePath."
}
$destExe = Join-Path $destDir "notifling.exe"
Copy-Item -LiteralPath (Resolve-Path $ExePath).Path -Destination $destExe -Force
Write-Host "Copied to $destExe"

$shell = New-Object -ComObject WScript.Shell

# 1. Startup shortcut -> runs at login.
$startupLnk = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup\Notifling.lnk"
$sc = $shell.CreateShortcut($startupLnk)
$sc.TargetPath = $destExe
$sc.Arguments = "--minimized"
$sc.WorkingDirectory = $destDir
$sc.Description = "Notifling phone notification forwarder"
$sc.Save()
Write-Host "Startup shortcut: $startupLnk"

# 2. Start Menu shortcut -> required for toast notifications (AppID) to render.
$menuLnk = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Notifling.lnk"
$sc2 = $shell.CreateShortcut($menuLnk)
$sc2.TargetPath = $destExe
$sc2.WorkingDirectory = $destDir
$sc2.Description = "Notifling"
$sc2.Save()
Write-Host "Start Menu shortcut: $menuLnk"

# 3. Firewall rule for inbound UDP (needs admin; warn and continue otherwise).
if (-not $NoFirewall) {
    try {
        $rule = Get-NetFirewallRule -DisplayName "Notifling-UDP" -ErrorAction SilentlyContinue
        if ($null -eq $rule) {
            New-NetFirewallRule -DisplayName "Notifling-UDP" -Direction Inbound `
                -Protocol UDP -LocalPort $Port -Action Allow | Out-Null
            Write-Host "Firewall rule added for UDP $Port"
        } else {
            Write-Host "Firewall rule already exists"
        }
    } catch {
        Write-Warning "Could not add firewall rule (run PowerShell as Admin once): $_"
        Write-Host  "Manual: New-NetFirewallRule -DisplayName Notifling-UDP -Direction Inbound -Protocol UDP -LocalPort $Port -Action Allow"
    }
}

Write-Host ""
Write-Host "Done. Notifling will start at next login."
Write-Host "First run: double-click $destExe once to generate the key + qr.png, then scan it from the Android app."
