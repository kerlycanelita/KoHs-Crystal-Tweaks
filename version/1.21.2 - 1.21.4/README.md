# Compatibility status — Minecraft 1.21.2–1.21.4

## Build matrix

- Mod version: `1.0.0+mc1.21.2`
- Minecraft: `>=1.21.2 <1.21.5`
- Java: 21
- Yarn mappings: `1.21.2+build.1`
- Fabric Loader: `0.17.2`
- Fabric API: `0.106.1+1.21.2`
- Artifact: `kohs-crystal-tweaks-1.0.0+mc1.21.2.jar`
- Distribution status: GitHub beta/pre-release

## Verified behavior

- This group retains the older renderer API that remains compatible through 1.21.4.
- Custom sound replaces `minecraft:entity.generic.explode` through `SoundManager` and `SoundLoader` without the previous manual-playback delay.
- `Anchor Charge` was removed; this branch keeps only vanilla explosion replacement.
- The configuration screen preserves its manual background and avoids unwanted vanilla blur.
- The upper bound prevents false binary compatibility with 1.21.5 internals.

The preserved build completed `gradlew.bat clean build` successfully on 2026-04-24. Placement Fix is not included in this legacy branch; it starts with Minecraft 1.21.10.
