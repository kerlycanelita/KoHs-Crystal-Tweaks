# KoHs Crystal Tweaks — Minecraft 1.21.10

## Build matrix

- Mod version: `2.0.1+mc1.21.10`
- Minecraft: `1.21.10`
- Java: 21
- Yarn mappings: `1.21.10+build.3`
- Fabric Loader: `0.17.2`
- Fabric API: `0.138.4+1.21.10`
- Artifact: `kohs-crystal-tweaks-2.0.1+mc1.21.10.jar`

## Release 2 implementation

- Placement Fix preserves physical hotbar/attack/use ordering and retargets only the current accepted vanilla interaction.
- Ordinary single clicks remain on Minecraft's direct input path; ordered replay is used only for multi-action or slot-sensitive same-tick cycles.
- Rapid Attack Fix keeps one validated prediction attack pending until the matching real entity ID is available.
- Outgoing attacks remove the matching real crystal locally without depending on Local Crystal prediction, restoring immediate explosion feedback with the default configuration.
- Safe Crystal is switchable, defaults to OFF, and protects only normal obsidian.
- Local Crystal and Seamless Mode default to OFF; Placement Fix, Rapid Attack Fix, and Flotation default to ON.
- Timing-critical disable dialogs are bilingual; all descriptions use hover tooltips.
- Runtime custom sound, rendering controls, and the early incompatibility guard are included.
- No input is synthesized, no cooldown is removed, and no extra placement or attack packet is created.

## Verified build

```powershell
.\gradlew.bat clean build --no-daemon
```

The build and all 10 regression tests completed successfully on 2026-07-13. Minecraft was not launched.
