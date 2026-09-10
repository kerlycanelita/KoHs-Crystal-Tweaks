<#
.SYNOPSIS
    Validates and publishes one exact-target Modrinth release per Minecraft version.
.DESCRIPTION
    Reads MODRINTH_TOKEN from the process environment. Never stores or prints it.
    Run with -WhatIf first. All files and dependencies are checked before uploading.
#>
[CmdletBinding()]
param(
    [string[]] $Only,
    [ValidateSet('release', 'beta', 'alpha')]
    [string] $Channel = 'release',
    [string] $JarDirectory,
    [switch] $WhatIf
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Net.Http
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (-not $JarDirectory) { $JarDirectory = Join-Path $repositoryRoot 'versions' }
$JarDirectory = (Resolve-Path -LiteralPath $JarDirectory).Path
$token = $env:MODRINTH_TOKEN
if (-not $WhatIf -and [string]::IsNullOrWhiteSpace($token)) {
    throw 'MODRINTH_TOKEN must be set in the process environment before publication.'
}
$modVersion = (Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' } |
    ForEach-Object { ($_ -split '=', 2)[1].Trim() })
$changelogFile = Join-Path $repositoryRoot "docs\releases\modrinth-changelog-$modVersion.md"
if (-not (Test-Path -LiteralPath $changelogFile)) { throw "Missing changelog: $changelogFile" }
# Cast removes PowerShell's provider properties before JSON serialization.
$changelog = [string](Get-Content -Encoding UTF8 -LiteralPath $changelogFile -Raw)
if ([string]::IsNullOrWhiteSpace($changelog)) { throw 'Changelog is empty.' }

$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(90)
$client.DefaultRequestHeaders.UserAgent.ParseAdd('CrystalTweaks-Release/1.0')
if ($token) { $client.DefaultRequestHeaders.TryAddWithoutValidation('Authorization', $token) | Out-Null }

function Get-Api([string] $route) {
    $response = $client.GetAsync("https://api.modrinth.com/v2/$route").GetAwaiter().GetResult()
    try {
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Modrinth GET $route failed (HTTP $([int]$response.StatusCode))."
        }
        return ($body | ConvertFrom-Json)
    } finally { $response.Dispose() }
}

function Find-Dependency([string] $slug, [string] $minecraft, [string] $version) {
    $games = [uri]::EscapeDataString((ConvertTo-Json -InputObject @($minecraft) -Compress))
    $loaders = [uri]::EscapeDataString('["fabric"]')
    $available = @(Get-Api "project/$slug/version?game_versions=$games&loaders=$loaders")
    $match = $available | Where-Object {
        $_.version_number -eq $version -and $_.game_versions -contains $minecraft -and $_.loaders -contains 'fabric'
    } | Select-Object -First 1
    if (-not $match) { throw "No matching $slug $version release for Minecraft $minecraft." }
    return $match
}

try {
    $project = Get-Api 'project/kohs-crystal-tweaks'
    if ($project.id -ne 'dwVY5imH') { throw 'Unexpected Modrinth project.' }
    $existing = @(Get-Api "project/$($project.id)/version")
    $matrix = @{}
    Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle\versions.properties') |
        Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } |
        ForEach-Object {
            $parts = $_ -split '=', 2
            $matrix[$parts[0].Trim()] = @($parts[1] -split ',' | ForEach-Object { $_.Trim() })
        }

    $jars = @(Get-ChildItem -LiteralPath $JarDirectory -File -Filter "crystal-tweaks-*-$modVersion.jar")
    $plan = @()
    foreach ($jar in $jars) {
        $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            $entry = $archive.GetEntry('fabric.mod.json')
            if (-not $entry) { throw "Missing fabric.mod.json: $($jar.Name)" }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json }
            finally { $reader.Dispose() }
        } finally { $archive.Dispose() }
        $minecraft = $metadata.depends.minecraft
        if ($minecraft -isnot [string] -or -not $matrix.ContainsKey($minecraft)) {
            throw "Minecraft dependency is not one exact supported version: $($jar.Name)"
        }
        if ($Only -and $Only -notcontains $minecraft) { continue }
        if ($metadata.id -ne 'crystal_tweaks' -or $metadata.version -ne $modVersion -or
            $jar.Name -ne "crystal-tweaks-$minecraft-$modVersion.jar") {
            throw "JAR name and metadata do not match: $($jar.Name)"
        }
        $row = $matrix[$minecraft]
        if ($metadata.depends.fabricloader -ne ">=$($row[0])" -or
            $metadata.depends.java -ne ">=$($row[3])" -or
            -not $metadata.depends.'fabric-api' -or $metadata.environment -ne 'client') {
            throw "JAR dependencies do not match the build matrix: $($jar.Name)"
        }
        if ($plan.Minecraft -contains $minecraft) { throw "Duplicate target: $minecraft" }
        $hash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA512).Hash.ToLowerInvariant()
        $same = @($existing | Where-Object {
            $_.version_number -eq $modVersion -and $_.game_versions -contains $minecraft
        })
        if ($same.Count -gt 1) { throw "Multiple $modVersion releases for $minecraft; resolve before publishing." }
        $existingId = $null
        if ($same.Count -eq 1) {
            $remote = $same[0]
            $primary = @($remote.files | Where-Object primary)
            if ($remote.game_versions.Count -ne 1 -or $remote.loaders.Count -ne 1 -or
                $remote.loaders[0] -ne 'fabric' -or $remote.status -ne 'listed' -or
                $primary.Count -ne 1 -or $primary[0].hashes.sha512 -ne $hash) {
                throw "$modVersion for $minecraft already exists with different content or metadata. Use a new patch version."
            }
            $existingId = $remote.id
        }
        $api = Find-Dependency 'fabric-api' $minecraft $row[1]
        $menu = Find-Dependency 'modmenu' $minecraft $row[2]
        $data = [ordered]@{
            name = "Crystal Tweaks $modVersion - Minecraft $minecraft"
            version_number = $modVersion
            changelog = $changelog
            dependencies = @(
                @{ project_id = $api.project_id; version_id = $api.id; dependency_type = 'required' },
                @{ project_id = $menu.project_id; version_id = $menu.id; dependency_type = 'optional' }
            )
            game_versions = @($minecraft)
            version_type = $Channel
            loaders = @('fabric')
            featured = $true
            status = 'listed'
            project_id = $project.id
            file_parts = @('file')
            primary_file = 'file'
        }
        $plan += [pscustomobject]@{
            Minecraft = $minecraft; Path = $jar.FullName; Name = $jar.Name
            Hash = $hash; Data = $data; ExistingId = $existingId
        }
    }
    if (-not $plan.Count) { throw "No $modVersion JARs found." }
    foreach ($mc in $Only) {
        if ($plan.Minecraft -notcontains $mc) { throw "Missing requested target: $mc" }
    }
    foreach ($item in $plan) {
        $mode = if ($item.ExistingId) { 'SKIP: already verified' } else { 'READY' }
        Write-Output "$mode $($item.Name) [Minecraft $($item.Minecraft), Fabric API required, Mod Menu optional]"
    }
    if ($WhatIf) {
        Write-Output "Preflight passed for $($plan.Count) targets; nothing uploaded."
        return
    }

    foreach ($item in $plan) {
        if ($item.ExistingId) { continue }
        if ((Get-FileHash -LiteralPath $item.Path -Algorithm SHA512).Hash.ToLowerInvariant() -ne $item.Hash) {
            throw "File changed after preflight: $($item.Name)"
        }
        $form = [System.Net.Http.MultipartFormDataContent]::new()
        $json = ConvertTo-Json -InputObject $item.Data -Depth 6 -Compress
        $form.Add([System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json'), 'data')
        $stream = [IO.File]::OpenRead($item.Path)
        $file = [System.Net.Http.StreamContent]::new($stream)
        $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/java-archive')
        $form.Add($file, 'file', $item.Name)
        try {
            $response = $client.PostAsync('https://api.modrinth.com/v2/version', $form).GetAwaiter().GetResult()
            try {
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                if (-not $response.IsSuccessStatusCode) {
                    throw "Upload failed for $($item.Name) (HTTP $([int]$response.StatusCode)): $body"
                }
                $created = $body | ConvertFrom-Json
                $verified = Get-Api "version/$($created.id)"
                $primary = @($verified.files | Where-Object primary)
                if ($verified.status -ne 'listed' -or $verified.version_number -ne $modVersion -or
                    $verified.game_versions.Count -ne 1 -or $verified.game_versions[0] -ne $item.Minecraft -or
                    $primary.Count -ne 1 -or $primary[0].hashes.sha512 -ne $item.Hash) {
                    throw "Post-upload verification failed for version $($created.id)."
                }
                foreach ($dependency in $item.Data.dependencies) {
                    $found = @($verified.dependencies | Where-Object {
                        $_.project_id -eq $dependency.project_id -and
                        $_.version_id -eq $dependency.version_id -and
                        $_.dependency_type -eq $dependency.dependency_type
                    })
                    if ($found.Count -ne 1) { throw "Dependency mismatch on version $($created.id)." }
                }
                Write-Output "PUBLISHED $($item.Name) https://modrinth.com/mod/kohs-crystal-tweaks/version/$($created.id)"
            } finally { $response.Dispose() }
        } finally { $form.Dispose(); $stream.Dispose() }
    }
} finally { $client.Dispose() }
