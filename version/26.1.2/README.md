# KoHs Crystal Tweaks — Minecraft 26.1.2

## Build matrix

- Mod version: `2.0.1+mc26.1.2`
- Minecraft: `26.1.2`
- Java: 25
- Mappings: official Mojang names
- Fabric Loader: `0.19.3`
- Fabric API: `0.154.2+26.1.2`
- Artifact: `kohs-crystal-tweaks-2.0.1+mc26.1.2.jar`

## Release 2 implementation

- Full feature parity with the stable 1.21.11 branch, adapted to `MultiPlayerGameMode`, official names, and the submit renderer.
- Ordered keyboard/mouse input, exact causal Placement Fix, deduplicated Rapid Attack Fix, and switchable Safe Crystal.
- Ordinary single clicks stay on Minecraft's direct input path; replay is reserved for order-sensitive same-tick sequences.
- Real-crystal cleanup remains immediate when optional Local Crystal prediction is OFF.
- Fresh defaults: Placement Fix, Rapid Attack Fix, and Flotation ON; Local Crystal, Seamless, Safe Crystal, and Static Crystal OFF.
- Responsive tooltip-based Mod Menu interface with bilingual warnings only for timing-critical options.
- Runtime WAV/OGG/MP3 replacement through `SoundBufferLibrary` and `SoundEngine`.
- Early high-confidence mixin incompatibility guard with a mandatory bilingual shutdown screen.
- No synthesized input, guessed entity IDs, cooldown removal, retry loops, or extra interaction packets.

## Verified build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

The build and all 10 regression tests completed successfully on 2026-07-13. Minecraft was not launched.
