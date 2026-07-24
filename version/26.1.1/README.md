# KoHs Crystal Tweaks — Minecraft 26.1.1

## Build matrix

- Mod version: `2.0.4+mc26.1.1`
- Minecraft: `26.1.1`
- Java: 25
- Mappings: official Mojang names
- Fabric Loader: `0.19.3`
- Fabric API: `0.145.4+26.1.1`
- Artifact: `kohs-crystal-tweaks-2.0.4+mc26.1.1.jar`

## 2.0.4 safety behavior

- No custom client-identification or compatibility payloads.
- No sub-tick queue, delayed action, replay, retry, or automatic slot selection.
- At most one corresponding vanilla action per physical attack or use input.
- Render-only, non-targetable visual crystal preview; OFF by default.
- Optional cleanup only for the exact real, server-provided crystal selected by the player's normal attack; OFF by default.
- The server remains authoritative for placement, entity IDs, damage, and explosions.

Visual, sound, and manual safety controls remain available. No universal anti-cheat guarantee is claimed; server rules must be checked before use.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

Minecraft is not launched during automated release preparation.
