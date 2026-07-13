# KoHs Crystal Tweaks — Minecraft 1.21.11

## Build matrix

- Mod version: `1.1.0-beta.2+mc1.21.11`
- Minecraft: `>=1.21.11`
- Java: 21
- Yarn mappings: `1.21.11+build.5`
- Fabric Loader: `0.17.2`
- Fabric API: `0.140.0+1.21.11`
- Artifact: `kohs-crystal-tweaks-1.1.0-beta.2+mc1.21.11.jar`
- Distribution status: GitHub beta/pre-release

## Beta implementation

- Placement Fix is integrated into `ClientPlayerInteractionManager.interactBlock` and enabled by default.
- The `Tweaks` confirmation uses `Accept` to disable and `Restore` to keep the feature enabled.
- Local prediction runs only after `ActionResult.isAccepted()`.
- Valid bases match vanilla: obsidian or bedrock.
- The visual timeout starts at 12 ticks and adapts after successful pairing.
- Outer/core tint is selected by `ModelPart` identity during actual queued rendering.
- The screen layout is bounded by the current logical dimensions.

Placement Fix changes only the current interaction's `BlockHitResult`. It sends no extra packets, repeats no clicks, and automates no combat action.

## Verified build

```powershell
.\gradlew.bat clean build --no-daemon
```

Build completed successfully on 2026-07-13. Minecraft was not launched.
