# Compatibility status — Minecraft 1.21.6–1.21.8

## Build matrix

- Mod version: `1.0.0+mc1.21.6`
- Minecraft: `>=1.21.6 <1.21.9`
- Java: 21
- Yarn mappings: `1.21.6+build.1`
- Fabric Loader: `0.19.2`
- Fabric API: `0.128.2+1.21.6`
- Artifact: `kohs-crystal-tweaks-1.0.0+mc1.21.6.jar`
- Distribution status: GitHub beta/pre-release

## Verified behavior

- Uses the intermediate 1.21.6+ client API, separated from 1.21.5 because of real binary changes.
- Custom sound replaces `minecraft:entity.generic.explode` through the vanilla sound pipeline.
- `Anchor Charge` was removed; this branch keeps only vanilla explosion replacement.
- The configuration screen avoids unwanted vanilla blur.
- `SafeCrystalMixin` no longer relies on a binary-fragile player-world accessor.

The preserved build completed `gradlew.bat clean build` successfully on 2026-04-24. Placement Fix is not included in this legacy branch; it starts with Minecraft 1.21.10.
