# KoHs Crystal Tweaks — Minecraft 26.2

## Build matrix

- Mod version: `2.0.1+mc26.2`
- Minecraft: `26.2`
- Java: 25
- Mappings: official Mojang names
- Fabric Loader: `0.19.3`
- Fabric API: `0.154.2+26.2`
- Artifact: `kohs-crystal-tweaks-2.0.1+mc26.2.jar`

## Included fixes

- Complete release 2 behavior adapted to the 26.2 screen controller and `EntityTypes` registry changes.
- Exact causal Placement Fix with ordered keyboard and mouse input.
- Ordinary single clicks stay on Minecraft's direct input path; replay is reserved for order-sensitive same-tick sequences.
- Deduplicated Rapid Attack Fix that waits for the real server entity ID.
- Real-crystal cleanup remains immediate when optional Local Crystal prediction is OFF.
- Safe Crystal OFF by default and limited to normal obsidian.
- Local Crystal and Seamless Mode OFF by default.
- Tooltip-based responsive menu, runtime custom sound, visual controls, and bilingual timing warnings.
- Early incompatibility blocking and no synthesized actions, guessed IDs, retries, cooldown changes, or extra interaction packets.

## Verified build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

The build and all 10 regression tests completed successfully on 2026-07-13. Minecraft was not launched.
