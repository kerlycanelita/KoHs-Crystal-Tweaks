# KoHs Crystal Tweaks — Minecraft 1.21.11

## Build matrix

- Mod version: `2.0.0-beta.2+mc1.21.11`
- Minecraft: `>=1.21.11`
- Java: 21
- Yarn mappings: `1.21.11+build.5`
- Fabric Loader: `0.17.2`
- Fabric API: `0.140.0+1.21.11`
- Artifact: `kohs-crystal-tweaks-2.0.0-beta.2+mc1.21.11.jar`
- Distribution status: GitHub beta/pre-release

## Beta implementation

- Startup incompatibility detection runs from the Mixin configuration plugin before KoHs gameplay mixins are applied.
- `marlowcrystal` is explicitly blocked because both mods register the `marlowcrystal:opt_out` compatibility payload and modify the same client-side crystal cleanup/retargeting path.
- Unknown mods are blocked only for a direct KoHs-class target or an exact critical target-class/method overlap from a crystal-related mixin.
- The blocked path registers no KoHs prediction, sound, gameplay event, or compatibility-network services.
- The mandatory bilingual screen replaces Mod Menu, restores itself if another screen is opened, ignores Escape, and exposes only a clean-shutdown button.
- `Safe Crystal` is disabled by default and has a direct ON/OFF control in `Tweaks` without a confirmation dialog.
- When Safe Crystal is disabled, its two callbacks return before any player/world/block lookup and do not cancel vanilla block attacks or breaking progress.
- Existing configs without the new field migrate to `safeCrystalEnabled=false`; explicit saved user selections are preserved.
- Compact screens use a two-column Tweaks layout so all six controls stay inside the panel.
- `Rapid Attack Fix` is enabled by default in `Tweaks` and preserves one validated attack if the local prediction is clicked before the server crystal loads.
- Placement Fix preserves the physical order of vanilla attack/use presses during crystal cycles, including Use Item bound to a keyboard key.
- Each ordered entry must consume one existing vanilla `KeyBinding.wasPressed()` count; the implementation does not create input or remove the use cooldown.
- An accepted predicted crystal becomes the immediate target for a following physical attack in the same client tick.
- Pending attacks are deduplicated, require the real server entity ID, and expire with the prediction timeout.
- Real-crystal cleanup resolves the outgoing packet ID and immediately retraces the crosshair through prediction-aware raycasting.
- Placement Fix is integrated into `ClientPlayerInteractionManager.interactBlock` and enabled by default.
- The `Tweaks` confirmation uses `Accept` to disable and `Restore` to keep the feature enabled.
- Local prediction runs only after `ActionResult.isAccepted()`.
- Valid bases match vanilla: obsidian or bedrock.
- The visual timeout starts at 12 ticks and adapts after successful pairing.
- Outer/core tint is selected by `ModelPart` identity during actual queued rendering.
- The screen layout is bounded by the current logical dimensions.
- Feature explanations are hover tooltips; fixed description blocks have been removed.
- `Local Crystal`, `Seamless Mode`, `Placement Fix`, and `Rapid Attack Fix` retain English-first, Spanish-second `Accept` / `Restore` warnings.
- Visual, Sound, Safe Crystal, Static Crystal, and Crystal Flotation controls switch directly without a confirmation dialog.

Placement Fix changes only the current interaction's `BlockHitResult`. Rapid Attack Fix preserves at most one attack that already passed vanilla validation. Neither feature guesses IDs, repeats clicks, or selects remote targets.

## Verified build

```powershell
.\gradlew.bat clean build --no-daemon
```

Build completed successfully on 2026-07-13. Minecraft was not launched.
