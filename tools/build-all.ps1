<#
.SYNOPSIS
    Builds one Crystal Tweaks JAR per supported Minecraft version.

.DESCRIPTION
    Reads gradle/versions.properties, builds every listed Minecraft target, collects the JARs into
    versions/ and regenerates CHECKSUMS.sha256.

    Each target is built in its own Gradle invocation because build.gradle configures the Loom
    plugin, the source transforms and the Java release from the selected Minecraft version.

.PARAMETER Only
    Build just these Minecraft versions instead of the whole matrix.

.PARAMETER Offline
    Pass --offline to Gradle. Useful once every dependency is in the Gradle cache.

.EXAMPLE
    .\tools\build-all.ps1

.EXAMPLE
    .\tools\build-all.ps1 -Only 26.1.2, 26.2
#>
[CmdletBinding()]
param(
    [string[]] $Only,
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$matrixFile = Join-Path $repositoryRoot 'gradle\versions.properties'
$outputDirectory = Join-Path $repositoryRoot 'versions'
$gradlew = Join-Path $repositoryRoot 'gradlew.bat'

if (-not (Test-Path $matrixFile)) {
    throw "Build matrix not found at $matrixFile"
}

# Ordered so the published listing follows the matrix rather than alphabetical JAR names.
$targets = Get-Content $matrixFile |
    Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } |
    ForEach-Object { ($_ -split '=', 2)[0].Trim() }

if ($Only) {
    $unknown = $Only | Where-Object { $targets -notcontains $_ }
    if ($unknown) {
        throw "Not in the build matrix: $($unknown -join ', ')"
    }
    $targets = $Only
}

$modVersion = (Get-Content (Join-Path $repositoryRoot 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' } |
    ForEach-Object { ($_ -split '=', 2)[1].Trim() })

Write-Host "Crystal Tweaks $modVersion - building $($targets.Count) Minecraft target(s)" -ForegroundColor Cyan

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

# Every target writes into the same build\libs, so stale JARs from earlier versions accumulate there
# and make it hard to tell what this run actually produced.
$libraryDirectory = Join-Path $repositoryRoot 'build\libs'
if (Test-Path $libraryDirectory) {
    Remove-Item (Join-Path $libraryDirectory '*.jar') -Force -ErrorAction SilentlyContinue
}

$gradleArguments = @('build')
if ($Offline) { $gradleArguments += '--offline' }

$built = @()
$failed = @()

foreach ($target in $targets) {
    Write-Host ''
    Write-Host "==> Minecraft $target" -ForegroundColor Yellow

    & $gradlew @gradleArguments "-Pmc=$target"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    build failed" -ForegroundColor Red
        $failed += $target
        continue
    }

    $jarName = "crystal-tweaks-$target-$modVersion.jar"
    $jarPath = Join-Path $repositoryRoot "build\libs\$jarName"
    if (-not (Test-Path $jarPath)) {
        Write-Host "    expected $jarName in build\libs" -ForegroundColor Red
        $failed += $target
        continue
    }

    Copy-Item $jarPath (Join-Path $outputDirectory $jarName) -Force
    Write-Host "    $jarName" -ForegroundColor Green
    $built += $jarName
}

if ($built.Count -gt 0) {
    # Rewrite the checksum list from the JARs that are actually present, so a partial run never
    # leaves a stale hash behind for a file it did not produce.
    $checksumLines = Get-ChildItem $outputDirectory -Filter "crystal-tweaks-*-$modVersion.jar" |
        Sort-Object Name |
        ForEach-Object { "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)" }

    # Written by hand rather than with Set-Content: Windows PowerShell writes UTF-8 with a BOM and
    # CRLF line endings, and both make `sha256sum -c CHECKSUMS.sha256` fail for anyone verifying a
    # download on Linux or macOS.
    [System.IO.File]::WriteAllText(
        (Join-Path $repositoryRoot 'CHECKSUMS.sha256'),
        (($checksumLines -join "`n") + "`n"),
        (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ''
    Write-Host "CHECKSUMS.sha256 updated ($($checksumLines.Count) entries)" -ForegroundColor Cyan
}

Write-Host ''
Write-Host "built: $($built.Count)  failed: $($failed.Count)" -ForegroundColor Cyan
if ($failed.Count -gt 0) {
    Write-Host "failed targets: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
