# KoHs Crystal Tweaks — Minecraft 1.21.10

## Build matrix

- Mod version: `2.0.4+mc1.21.10`
- Minecraft: `1.21.10`
- Java: 21
- Yarn mappings: `1.21.10+build.3`
- Fabric Loader: `0.17.2`
- Fabric API: `0.138.4+1.21.10`
- Artifact: `kohs-crystal-tweaks-2.0.4+mc1.21.10.jar`

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
.\gradlew.bat clean build --no-daemon
```

Minecraft is not launched during automated release preparation.
