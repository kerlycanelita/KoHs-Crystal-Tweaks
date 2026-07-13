# Compatibility status — Minecraft 1.21.9

## Build matrix

- Mod version: `1.0.0+mc1.21.9`
- Minecraft: `>=1.21.9 <1.21.10`
- Java: 21
- Yarn mappings: `1.21.9+build.1`
- Fabric Loader: `0.17.2`
- Fabric API: `0.134.1+1.21.9`
- Artifact: `kohs-crystal-tweaks-1.0.0+mc1.21.9.jar`
- Distribution status: GitHub beta/pre-release

## Verified behavior

- Isolated from earlier versions because 1.21.9 uses the modern queued entity renderer.
- Seamless rendering targets `OrderedRenderCommandQueue` and `EndCrystalEntityRenderState`.
- Custom sound uses the vanilla explosion sound path without parallel-playback delay.
- Client Crystal and the optimizer remain client-side and do not automate attacks or create combat packets.
- Includes modern Marlow protocol identifiers for version, opt-out, and opt-out acknowledgement, with per-server state.
- The optimizer observes the vanilla interaction packet the player already sends and performs local visual cleanup only.

The preserved build completed `gradlew.bat clean build` successfully on 2026-04-24. Placement Fix is not included in this branch; it starts with Minecraft 1.21.10.
