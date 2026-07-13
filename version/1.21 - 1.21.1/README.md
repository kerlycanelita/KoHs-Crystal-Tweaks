# Compatibility status — Minecraft 1.21–1.21.1

## Build matrix

- Mod version: `1.0.0+mc1.21`
- Minecraft: `>=1.21 <1.21.2`
- Java: 21
- Yarn mappings: `1.21+build.9`
- Fabric Loader: `0.18.6`
- Fabric API: `0.102.0+1.21`
- Artifact: `kohs-crystal-tweaks-1.0.0+mc1.21.jar`
- Distribution status: GitHub beta/pre-release

## Verified behavior

- Custom sound replaces `minecraft:entity.generic.explode` through the vanilla sound pipeline instead of parallel manual playback.
- WAV, OGG, and MP3 imports remain supported.
- `Anchor Charge` was removed; this branch only manages the vanilla explosion replacement.
- The configuration screen keeps its custom background isolated from vanilla blur.
- The declared range is intentionally limited to 1.21–1.21.1.

The preserved build completed `gradlew.bat clean build` successfully on 2026-04-23. Placement Fix is not included in this legacy branch; it starts with Minecraft 1.21.10.
