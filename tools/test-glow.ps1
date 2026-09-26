$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $repositoryRoot 'build/verification/glow'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sourceDirectory = Join-Path $repositoryRoot 'src/client/java/com/zymekoh/crystaltweaks/client'
& javac --release 21 -d $outputDirectory (Join-Path $sourceDirectory 'CrystalAppearance.java') (Join-Path $sourceDirectory 'CrystalScreenLayout.java') (Join-Path $sourceDirectory 'CrystalFlashStyle.java') (Join-Path $sourceDirectory 'CrystalGlowMath.java') (Join-Path $sourceDirectory 'AfterglowTimeline.java') (Join-Path $repositoryRoot 'src/client/java/com/zymekoh/crystaltweaks/core/CrystalOptimizerGuard.java') (Join-Path $PSScriptRoot 'tests/CrystalAppearanceTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Glow test compilation failed' }
& java -cp $outputDirectory CrystalAppearanceTest
if ($LASTEXITCODE -ne 0) { throw 'Glow tests failed' }
& javac --release 21 -cp $outputDirectory -d $outputDirectory (Join-Path $PSScriptRoot 'tests/ScreenLayoutTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Layout test compilation failed' }
& java -cp $outputDirectory ScreenLayoutTest
if ($LASTEXITCODE -ne 0) { throw 'Layout tests failed' }
