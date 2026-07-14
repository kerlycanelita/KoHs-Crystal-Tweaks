# KoHs Crystal Tweaks — Minecraft 26.1

## Build matrix

- Mod version: `2.0.1+mc26.1`
- Minecraft: `26.1`
- Java: 25
- Mappings: official Mojang names
- Fabric Loader: `0.19.3`
- Fabric API: `0.145.1+26.1`
- Artifact: `kohs-crystal-tweaks-2.0.1+mc26.1.jar`

## Included fixes

- Exact causal Placement Fix with ordered keyboard and mouse input.
- Ordinary single clicks stay on Minecraft's direct input path; replay is reserved for order-sensitive same-tick sequences.
- Deduplicated Rapid Attack Fix that waits for the real server entity ID.
- Real-crystal cleanup remains immediate when optional Local Crystal prediction is OFF.
- Safe Crystal OFF by default and limited to normal obsidian.
- Local Crystal and Seamless Mode OFF by default.
- Tooltip-based responsive menu and bilingual timing warnings.
- Runtime custom sound, complete visual controls, and early incompatibility blocking.
- No synthesized actions, guessed IDs, retries, cooldown changes, or extra interaction packets.

## Verified build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

The build and all 10 regression tests completed successfully on 2026-07-13. Minecraft was not launched.
