# Changelog

## 2.0.1 — 2026-07-13

### Minecraft 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, and 26.2

- Keeps ordinary single attack/use clicks entirely on Minecraft's direct input path.
- Reserves ordered replay for multi-action or slot-sensitive same-tick sequences whose physical order vanilla could otherwise collapse.
- Keeps outgoing real-crystal cleanup active independently of optional Local Crystal prediction, restoring immediate explosion feedback with the default Local Crystal OFF state.
- Retains exact causal placement retargeting, one validated pending attack, and the strict no-synthesized-input/no-extra-packet boundary.
- Restricts each artifact to its exact Minecraft target and verifies every maintained branch with 10 automated tests.

## 2.0.0 — 2026-07-13

### Minecraft 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, and 26.2

- Ported the complete tested release 2 implementation to every listed target with exact per-version metadata and dependencies.
- Preserves physical number-key, mouse-wheel, attack, and use ordering during same-tick crystal cycles.
- Retargets a crystal interaction only after vanilla accepts the preceding obsidian placement and only to that exact causal base.
- Keeps `Placement Fix`, `Rapid Attack Fix`, and `Crystal Flotation` enabled by default while `Local Crystal`, `Seamless Mode`, `Safe Crystal`, and `Static Crystal` start disabled on fresh installations.
- Keeps pending predicted attacks deduplicated until the server supplies the real crystal entity ID; no IDs are guessed and no attack is generated.
- Retains the early incompatibility guard for Marlow Crystal Optimizer and high-confidence mixin conflicts.
- Includes bilingual timing-feature warnings, compact hover descriptions, MIT metadata, and the regression suites (10 tests on 1.21.10 and 1.21.11; nine on each 26.x release-2 port).
- Restores runtime WAV/OGG/MP3 crystal sound replacement on the official-name 26.x branches.
- Adapts the 26.2 screen ownership and entity registry changes while keeping the same user-facing behavior.
- On Minecraft 1.21.10 and 1.21.11, keeps ordinary single clicks on the direct vanilla path and decouples real-crystal attack cleanup from optional Local Crystal prediction, removing the placement/explosion slowdown reported with either Safe Crystal state.
- Restricts the 1.21.11 artifact to Minecraft 1.21.11 exactly, preventing its intermediary-named classes from being loaded accidentally on official-name 26.x installations.
- Contains no automation, synthesized input, cooldown removal, interaction retry, remote targeting, or extra packets.

## 2.0.0-beta.4 — 2026-07-13

### Minecraft 1.21.11

- Fixed Placement Fix replaying several same-tick uses with only Minecraft's final selected hotbar item.
- Added physical ordering for number-key and mouse-wheel slot changes alongside real attack/use presses.
- Ignores keyboard repeat events and still requires every replayed attack/use to consume an existing vanilla `wasPressed()` count.
- Records an obsidian base only after vanilla accepts the placement; a crystal input received first is never delayed, retried, or placed later.
- Removed the broad adjacent-block fallback: retargeting now accepts only the recorded base or its exact placement offset.
- Reordered validation so an already-valid vanilla hit or an unrelated hit avoids redundant pending-base entity scans.
- Changed fresh-install defaults for `Local Crystal` and `Seamless Mode` to OFF.
- Preserved existing saved choices and the strict legitimacy boundary: no guessed entity IDs, generated actions, cooldown removal, or additional packets.
- Added ordered-slot and exact-retarget regression tests; the suite now contains nine tests.

## 2.0.0-beta.3 — 2026-07-13

### Minecraft 1.21.11

- Limited `Safe Crystal` protection to normal obsidian.
- Removed crying obsidian from both the initial block-attack and continued breaking-progress guards.
- Updated the Tweaks hover description to reflect the narrower scope.
- Kept Safe Crystal OFF by default and without a confirmation dialog.

## 2.0.0-beta.2 — 2026-07-13

### Minecraft 1.21.11

- Changed `Safe Crystal` to OFF by default as requested.
- Existing pre-2.0 configuration files without `safeCrystalEnabled` now migrate to OFF.
- Preserves an explicit user selection after the switch is changed and saved.
- Keeps the direct no-warning toggle and vanilla pass-through behavior from beta.1.

## 2.0.0-beta.1 — 2026-07-13

### Minecraft 1.21.11

- Started the release 2 beta line.
- Added a persistent `Safe Crystal` ON/OFF switch to the `Tweaks` tab, enabled by default.
- Disabling it is immediate and never opens an `Accept` / `Restore` warning.
- The disabled mixin path returns before reading player, world, held-item, or block-state data and never cancels vanilla `attackBlock` or `updateBlockBreakingProgress`.
- Preserved the exact scope of Safe Crystal when enabled: it protects obsidian and crying obsidian only while an End Crystal is held.
- Added automatic migration for existing configuration files that do not contain `safeCrystalEnabled`; they retain the previous enabled behavior.
- Arranged the six Tweaks controls into two columns on compact logical screens to prevent overlap with the Close button.
- Added a configuration migration test; the existing five incompatibility-scanner tests remain active.

## 1.1.0-beta.7 — 2026-07-13

### Minecraft 1.21.11

- Moved the incompatibility-screen title into a dedicated header gap below the top frame line.
- Derived the report's top edge from the actual title position and font height so GUI scaling cannot make the border cross the heading.
- No incompatibility detection, warning content, controls, or gameplay behavior changed.

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
