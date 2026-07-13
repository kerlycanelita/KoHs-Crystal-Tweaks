# KoHs Crystal Tweaks

KoHs Crystal Tweaks is a client-side Fabric mod focused on legitimate Crystal PvP quality-of-life improvements. It provides immediate local crystal feedback, smoother reconciliation with the server entity, custom crystal colors, safety controls, and optional explosion sound replacement.

## Features

- Placement Fix on 1.21.10+ retargets the current vanilla use to freshly predicted obsidian without extra packets or automation.
- Local Crystal makes accepted placements appear immediately on the client.
- Seamless Mode smooths the transition between the local crystal and the real server crystal.
- The legitimate Crystal Optimizer performs visual client-side cleanup after the player's vanilla attack without automating combat.
- Crystal Tint recolors the End Crystal frame and core independently.
- Custom Sound imports WAV, OGG, and MP3 files on supported branches.
- Safe Crystal prevents accidental block breaking while holding an End Crystal.
- The custom configuration screen preserves its own background instead of inheriting unwanted vanilla blur.

## Supported versions

- `1.21–1.21.1`
- `1.21.2–1.21.4`
- `1.21.5`
- `1.21.6–1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`
- `26.1.2`

## Notes

- Client-side Fabric mod.
- All GitHub downloads are currently marked as beta/pre-release builds.
- Modern 1.21.9+ branches include the modern Marlow protocol identifiers (`version`, `opt_out`, and `opt_out_ack`) so a server can request that the optimizer be disabled.
- `Anchor Charge` was removed; the custom sound system focuses on replacing vanilla explosion audio.
- Version groups are split at real internal API boundaries to avoid false binary compatibility.
- Every directory under `version/` builds independently.

## Build

```powershell
.\gradlew.bat clean build --no-daemon
```
