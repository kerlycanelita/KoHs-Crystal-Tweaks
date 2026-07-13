# Changelog

## 1.1.0-beta.6 — 2026-07-13

### Minecraft 1.21.11

- Added an early incompatibility guard for known crystal optimizers, direct mutations of KoHs classes, and exact class/method overlaps in timing-critical crystal mixins.
- Explicitly blocks `marlowcrystal`; a confirmed beta.5 crash showed KoHs and Marlow both registering `marlowcrystal:opt_out`, causing Fabric to reject the duplicate payload during client entrypoint initialization.
- Disables every KoHs gameplay mixin through an `IMixinConfigPlugin` before application when a conflict is found.
- Skips KoHs prediction, placement, sound, event, and compatibility-network initialization while blocked, preventing the duplicate Marlow payload registration.
- Replaces Mod Menu and normal gameplay with a mandatory responsive incompatibility screen that identifies each conflicting mod and provides technical reasons in English and Spanish.
- Prevents Escape or screen replacement from bypassing the warning; the only action schedules a clean Minecraft shutdown.
- Limits generic detection to high-confidence evidence so a shared Minecraft class or an unrelated method does not cause a false positive.
- Added bytecode-scanner tests for real KoHs hooks, exact overlaps, different methods, and unrelated mods.

## 1.1.0-beta.5 — 2026-07-13

### Minecraft 1.21.11

- Fixed keyboard-place and mixed mouse/keyboard crystal cycles losing their physical input order inside one client tick.
- Records only real vanilla attack/use press events and consumes each ordered entry against its corresponding `KeyBinding.wasPressed()` count.
- Immediately targets an accepted local crystal placement so the next physical attack in the same tick can reach the prediction.
- Preserved server authority and the one-input/one-action boundary: no removed cooldown, synthesized click, guessed entity ID, or additional packet.
- Removed disable confirmations from Visual and Sound.
- Removed disable confirmations from `Static Crystal` and `Crystal Flotation`; warnings remain for the four timing-critical optimization/fix toggles.

## 1.1.0-beta.4 — 2026-07-13

### Minecraft 1.21.11

- Fixed rapid place/attack cycles stalling after the optimizer removed a real crystal but left the crosshair on the dead entity.
- Resolved optimizer targets from the outgoing attack packet's real entity ID instead of assuming the current crosshair still matched.
- Retraced the crosshair immediately after client-side cleanup and excluded both removed crystals and KoHs local predictions from the new target.
- Kept the one-input/one-action boundary: no generated clicks, automatic retry loop, guessed entity ID, or additional attack packet.
- Added `Accept` / `Restore` confirmation before disabling every toggle.
- Added concise bilingual consequences to each warning, with English first and Spanish second.
- Embedded the upstream Marlow MIT attribution in the repository and the distributed JAR.

## 1.1.0-beta.3 — 2026-07-13

### Minecraft 1.21.11

- Added `Rapid Attack Fix`, enabled by default in the `Tweaks` tab.
- Fixed a lost-attack race when very fast left/right butterfly clicks hit a predicted crystal before the matching server entity is loaded.
- Deferred at most one already validated vanilla attack until the real crystal ID is available.
- Deduplicated repeated clicks and bounded the pending intent by the existing prediction timeout; no guessed IDs or packet spam are used.
- Removed fixed option-description blocks and moved their content to hover tooltips across the configuration screen.
- Preserved the responsive compact layout after adding the new toggle.

## 1.1.0-beta.2 — 2026-07-13

### Minecraft 1.21.10, 1.21.11, and 26.1.2

- Converted the complete Placement Fix confirmation dialog to English.
- Replaced all four former non-English dialog strings with `Disable Placement Fix?`, an English delay warning, `Accept`, and `Restore`.
- Audited user-facing literals across every maintained source set; no other Spanish UI strings were found.
- Translated the remaining historical compatibility changelogs to English.
- Verified that Fabric/Mod Menu metadata declares the MIT license in every maintained source set.

## 1.1.0-beta.1 — 2026-07-12

### Minecraft 1.21.10, 1.21.11, and 26.1.2

- Integrated KoHs Crystal Placement Fix into KoHs Crystal Tweaks.
- Added persistent `placementFixEnabled=true` configuration.
- Added the `Placement Fix` toggle to `Tweaks`, with an `Accept` / `Restore` confirmation when disabling it.
- Limited retargeting to the current vanilla interaction, with no additional packets or automation.
- Moved local prediction to the accepted interaction result.
- Raised the initial adaptive timeout to the configured value.
- Aligned prediction bases with vanilla: obsidian or bedrock.
- Applied per-part tint when queued geometry is actually rendered.

### Minecraft 1.21.10 and 1.21.11

- Replaced fragmented rendering that changed `visible` on queued model parts.
- Made the configuration screen responsive at compact logical sizes and high GUI scales.

### Minecraft 26.1.2

- Ported separate frame/core tint rendering.
- Ported `Spin Speed`, `Crystal Flotation`, and `Static Crystal` to the model and beam.
- Integrated Placement Fix using official Mojang names and Java 25.

## 1.0.0 compatibility builds

- Preserved separate artifacts for 1.21–1.21.1, 1.21.2–1.21.4, 1.21.5, 1.21.6–1.21.8, and 1.21.9.
- Published those verified artifacts through the GitHub pre-release channel without changing their internal mod versions.
