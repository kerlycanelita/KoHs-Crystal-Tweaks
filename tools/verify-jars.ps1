<#
.SYNOPSIS
    Checks that every built JAR declares the dependencies it was actually built against.

.DESCRIPTION
    Reads each JAR in versions\ and compares its fabric.mod.json against the JAR manifest and
    gradle/versions.properties.

    The check that matters most is the Fabric Loader floor: a JAR that demands a loader newer than
    the one it was built against will refuse to load for players, and nothing in the normal build
    output makes that visible.

.PARAMETER Path
    Directory holding the JARs. Defaults to versions\.
#>
[CmdletBinding()]
param(
    [string] $Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (-not $Path) { $Path = Join-Path $repositoryRoot 'versions' }

if (-not (Test-Path $Path)) {
    throw "No JAR directory at $Path. Run tools\build-all.ps1 first."
}

$modVersion = (Get-Content (Join-Path $repositoryRoot 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' } |
    ForEach-Object { ($_ -split '=', 2)[1].Trim() })

$matrix = @{}
Get-Content (Join-Path $repositoryRoot 'gradle\versions.properties') |
    Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } |
    ForEach-Object {
        $parts = $_ -split '=', 2
        $values = $parts[1] -split ',' | ForEach-Object { $_.Trim() }
        $matrix[$parts[0].Trim()] = [pscustomobject]@{
            Loader  = $values[0]
            Java    = $values[3]
        }
    }

function Read-JarEntry {
    param([string] $JarPath, [string] $EntryName)

    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry($EntryName)
        if (-not $entry) { return $null }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $archive.Dispose()
    }
}

$problems = 0
$checked = 0

foreach ($jar in Get-ChildItem $Path -Filter '*.jar' | Sort-Object Name) {
    $metadata = Read-JarEntry $jar.FullName 'fabric.mod.json' | ConvertFrom-Json
    # Manifest values wrap at 72 columns with a leading space on continuation lines.
    $manifest = (Read-JarEntry $jar.FullName 'META-INF/MANIFEST.MF') -replace "`r`n ", '' -replace "`n ", ''

    $builtLoader = ($manifest -split "`n" | Where-Object { $_ -like 'Fabric-Loader-Version:*' }) -replace '^[^:]+:\s*', ''
    $builtLoader = $builtLoader.Trim()
    $builtMinecraft = ($manifest -split "`n" | Where-Object { $_ -like 'Fabric-Minecraft-Version:*' }) -replace '^[^:]+:\s*', ''
    $builtMinecraft = $builtMinecraft.Trim()

    $declaredLoader = $metadata.depends.fabricloader
    $declaredMinecraft = $metadata.depends.minecraft
    $declaredJava = $metadata.depends.java

    $issues = @()

    if ($declaredLoader -ne ">=$builtLoader") {
        $issues += "loader declares '$declaredLoader' but was built against $builtLoader"
    }
    if ($declaredMinecraft -ne $builtMinecraft) {
        $issues += "minecraft declares '$declaredMinecraft' but was built against $builtMinecraft"
    }
    if ($matrix.ContainsKey($builtMinecraft)) {
        $expected = $matrix[$builtMinecraft]
        if ($builtLoader -ne $expected.Loader) {
            $issues += "loader $builtLoader does not match the matrix entry $($expected.Loader)"
        }
        if ($declaredJava -ne ">=$($expected.Java)") {
            $issues += "java declares '$declaredJava' but the matrix says $($expected.Java)"
        }
    } else {
        $issues += "Minecraft $builtMinecraft is not in gradle/versions.properties"
    }

    # Every class named in the Mixin config must exist in the JAR. A Mixin config with
    # "required": true aborts the game at startup when one is missing, and nothing in a normal build
    # catches it: @Invoker and @Accessor targets are not validated at compile time, so a per-version
    # source exclusion that is not matched by a config exclusion compiles cleanly and crashes on
    # launch for exactly the Minecraft versions it excluded.
    $mixinConfig = Read-JarEntry $jar.FullName 'crystal_tweaks.client.mixins.json' | ConvertFrom-Json
    $mixinPackagePath = $mixinConfig.package -replace '\.', '/'
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $entryNames = $archive.Entries | ForEach-Object { $_.FullName }
    } finally {
        $archive.Dispose()
    }

    foreach ($mixinName in @($mixinConfig.client) + @($mixinConfig.mixins)) {
        if (-not $mixinName) { continue }
        if ($entryNames -notcontains "$mixinPackagePath/$mixinName.class") {
            $issues += "mixin config lists $mixinName but the class is not in the JAR"
        }
    }

    # The JAR name must match the version the project is on. build\libs keeps every JAR ever built,
    # so a script that copies a hard-coded file name silently ships a stale artifact after a bump.
    if ($jar.Name -notmatch "-$([regex]::Escape($modVersion))\.jar$") {
        $issues += "file is not version $modVersion; build\libs keeps old JARs and one was picked up"
    }

    $checked++
    if ($issues.Count -eq 0) {
        Write-Host ("  OK   {0}" -f $jar.Name) -ForegroundColor Green
    } else {
        $problems++
        Write-Host ("  FAIL {0}" -f $jar.Name) -ForegroundColor Red
        $issues | ForEach-Object { Write-Host "         $_" -ForegroundColor Red }
    }
}

Write-Host ''
Write-Host "checked: $checked  problems: $problems" -ForegroundColor Cyan
if ($problems -gt 0) { exit 1 }
