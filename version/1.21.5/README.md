# Compatibility status — Minecraft 1.21.5

## Build matrix

- Mod version: `1.0.0+mc1.21.5`
- Minecraft: `>=1.21.5 <1.21.6`
- Java: 21
- Yarn mappings: `1.21.5+build.1`
- Fabric Loader: `0.17.2`
- Fabric API: `0.128.2+1.21.5`
- Artifact: `kohs-crystal-tweaks-1.0.0+mc1.21.5.jar`
- Distribution status: GitHub beta/pre-release

## Verified behavior

- Uses the 1.21.5 `ModelPart` API with `originY` and `applyTransform(...)`.
- Custom sound replaces `minecraft:entity.generic.explode` through the vanilla sound pipeline.
- `Anchor Charge` was removed; this branch keeps only vanilla explosion replacement.
- The configuration screen avoids unwanted vanilla blur.
- `SafeCrystalMixin` avoids the binary-fragile call that caused a reported 1.21.5 crash.

The preserved build completed `gradlew.bat clean build` successfully on 2026-04-24. Placement Fix is not included in this legacy branch; it starts with Minecraft 1.21.10.
