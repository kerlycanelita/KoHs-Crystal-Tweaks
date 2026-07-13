# KoHs Crystal Tweaks — Minecraft 26.1.2

## Build matrix

- Mod version: `1.1.0-beta.1+mc26.1.2`
- Minecraft: `~26.1.2`
- Java: 25
- Mappings: official Mojang names
- Fabric Loader: `0.19.3`
- Fabric API: `0.153.0+26.1.2`
- Artifact: `kohs-crystal-tweaks-1.1.0-beta.1+mc26.1.2.jar`
- Distribution status: GitHub beta/pre-release

## Beta implementation

- Placement Fix is integrated into `MultiPlayerGameMode.useItemOn` and enabled by default.
- The `Tweaks` confirmation uses `Aceptar` (Accept) to disable and `Restablecer` (Restore) to keep the feature enabled.
- Local prediction runs only after `InteractionResult.Success`.
- The visual timeout starts at 12 ticks and adapts after successful pairing.
- Frame/core tint is selected per part during actual queued rendering.
- `Spin Speed`, `Crystal Flotation`, and `Static Crystal` are connected to both the model and beam.
- The responsive panel, tabs, buttons, and picker remain inside logical screen bounds.

Placement Fix changes only the current interaction's `BlockHitResult`. It sends no extra packets, repeats no clicks, and automates no combat action.

## Verified build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

Build completed successfully on 2026-07-12. Minecraft was not launched. Runtime custom sound replacement remains incomplete on this branch; see `PORTING_ANALYSIS.md`.

