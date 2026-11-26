# Builds the APK and publishes it as a GitHub Release together with version.json.
# The installed app checks https://github.com/<repo>/releases/latest/download/version.json.
#
# Before running: bump versionCode/versionName in app/build.gradle.kts, run `gh auth login` once.
# Usage:  .\tools\release.ps1 -Notes "What's new"
param([string]$Notes = "")

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot)

$gradle = Get-Content app\build.gradle.kts -Raw
$versionCode = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$versionName = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $versionCode -or -not $versionName) { throw "Could not read version from app/build.gradle.kts" }

$repo = gh repo view --json nameWithOwner -q .nameWithOwner
if (-not $repo) { throw "No GitHub repo found for this folder" }
$base = "https://github.com/$repo/releases/latest/download"

if (gh release view "v$versionName" 2>$null) { throw "Release v$versionName already exists: bump the version first" }

.\gradlew.bat :app:assembleDebug "-PupdateUrl=$base/version.json"
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$out = "build\release"
New-Item -ItemType Directory -Force $out | Out-Null
$apk = Join-Path $out "sport-assistant.apk"
Copy-Item app\build\outputs\apk\debug\app-debug.apk $apk -Force

$json = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    apkUrl      = "$base/sport-assistant.apk"
    sha256      = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
    notes       = $Notes
} | ConvertTo-Json
$jsonPath = Join-Path $out "version.json"
[System.IO.File]::WriteAllText((Resolve-Path $out).Path + "\version.json", $json, (New-Object System.Text.UTF8Encoding($false)))

gh release create "v$versionName" $apk $jsonPath --title "v$versionName" --notes ($(if ($Notes) { $Notes } else { "Release $versionName" }))
Write-Host "Published v$versionName -> $base/version.json"
