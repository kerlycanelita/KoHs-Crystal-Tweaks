<#
.SYNOPSIS
    Publishes one Modrinth version per Minecraft target, files included.

.DESCRIPTION
    Reads the token from the MODRINTH_TOKEN environment variable. The token is never written to a
    file, never passed on the command line, and never printed: a command line is visible to every
    process on the machine and ends up in shell history.

    Set it for the current window only, so it disappears when you close it:

        $env:MODRINTH_TOKEN = 'mrp_...'
        .\tools\publish-modrinth.ps1 -WhatIf
        .\tools\publish-modrinth.ps1

    Run with -WhatIf first. It performs every check and prints exactly what would be created without
    sending anything.

.PARAMETER Only
    Publish just these Minecraft versions instead of every JAR found.

.PARAMETER Channel
    release, beta or alpha. Defaults to release.

.PARAMETER WhatIf
    Validate and print the plan without creating anything.
#>
[CmdletBinding()]
param(
    [string[]] $Only,
    [ValidateSet('release', 'beta', 'alpha')]
    [string] $Channel = 'release',
    [switch] $WhatIf
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$projectSlug = 'kohs-crystal-tweaks'
$versionsDirectory = Join-Path $repositoryRoot 'versions'
$changelogFile = Join-Path $repositoryRoot 'docs\modrinth-changelog-2.2.9.md'

$token = $env:MODRINTH_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "MODRINTH_TOKEN is not set. Run:  `$env:MODRINTH_TOKEN = 'mrp_...'"
}

$modVersion = (Get-Content (Join-Path $repositoryRoot 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' } |
    ForEach-Object { ($_ -split '=', 2)[1].Trim() })

# Loaders and the Fabric API dependency come from the same matrix the build uses, so a published
# version can never claim support the JAR was not built for.
$matrix = @{}
Get-Content (Join-Path $repositoryRoot 'gradle\versions.properties') |
    Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } |
    ForEach-Object {
        $parts = $_ -split '=', 2
        $values = $parts[1] -split ',' | ForEach-Object { $_.Trim() }
        $matrix[$parts[0].Trim()] = @{ Loader = $values[0]; FabricApi = $values[1]; ModMenu = $values[2] }
    }

$targets = Get-ChildItem $versionsDirectory -Filter "crystal-tweaks-*-$modVersion.jar" -File |
    ForEach-Object {
        if ($_.Name -match "^crystal-tweaks-(.+)-$([regex]::Escape($modVersion))\.jar$") {
            [pscustomobject]@{ Minecraft = $Matches[1]; Path = $_.FullName; Name = $_.Name }
        }
    }

if ($Only) { $targets = $targets | Where-Object { $Only -contains $_.Minecraft } }
if (-not $targets) { throw "No $modVersion JARs found in $versionsDirectory" }

$changelog = if (Test-Path $changelogFile) { Get-Content $changelogFile -Raw } else { '' }

$headers = @{ Authorization = $token }
$existing = @()
try {
    $existing = (Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$projectSlug/version" `
        -Headers $headers -Method Get) | ForEach-Object { $_.name }
} catch {
    throw "Could not reach Modrinth or the token was rejected: $($_.Exception.Message)"
}

Write-Host "Crystal Tweaks KoHs $modVersion -> $projectSlug  [$Channel]" -ForegroundColor Cyan
Write-Host ""

$published = 0
foreach ($t in $targets | Sort-Object Minecraft) {
    $versionName = "$modVersion+mc$($t.Minecraft)"
    $entry = $matrix[$t.Minecraft]
    if (-not $entry) {
        Write-Host ("  SKIP {0,-16} not in gradle/versions.properties" -f $t.Minecraft) -ForegroundColor Red
        continue
    }
    if ($existing -contains $versionName) {
        Write-Host ("  SKIP {0,-16} already published" -f $versionName) -ForegroundColor DarkGray
        continue
    }

    $body = @{
        name           = "Crystal Tweaks KoHs $versionName"
        version_number = $versionName
        changelog      = $changelog
        dependencies   = @()
        game_versions  = @($t.Minecraft)
        version_type   = $Channel
        loaders        = @('fabric')
        featured       = $false
        project_id     = $projectSlug
        file_parts     = @('file')
    }

    if ($WhatIf) {
        Write-Host ("  PLAN {0,-16} {1}  (Fabric API {2}, Mod Menu {3}, loader >={4})" -f `
            $versionName, $t.Name, $entry.FabricApi, $entry.ModMenu, $entry.Loader) -ForegroundColor Yellow
        continue
    }

    $form = @{
        data = ($body | ConvertTo-Json -Depth 5 -Compress)
        file = Get-Item $t.Path
    }
    try {
        Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/version' -Headers $headers `
            -Method Post -Form $form | Out-Null
        Write-Host ("  OK   {0,-16} {1}" -f $versionName, $t.Name) -ForegroundColor Green
        $published++
    } catch {
        Write-Host ("  FAIL {0,-16} {1}" -f $versionName, $_.Exception.Message) -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 400
}

Write-Host ""
if ($WhatIf) {
    Write-Host "Nothing was sent. Drop -WhatIf to publish." -ForegroundColor Yellow
} else {
    Write-Host "published: $published" -ForegroundColor Cyan
    Write-Host "Dependencies are not set by this script: add Fabric API (required) and Mod Menu" -ForegroundColor Yellow
    Write-Host "(optional) on each version page, using the versions above." -ForegroundColor Yellow
}
