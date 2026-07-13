# Technical investigation and decisions

## External signals

Public hit-crystal reports describe the same pattern: after quickly placing obsidian, the following End Crystal use can retain the previous target and fail to place. Client-side prediction mods also reduce perceived latency by rendering a local entity until the real server entity arrives.

Sources reviewed:

- [Community report: crystal fails to place when spamming after obsidian](https://www.reddit.com/r/MinecraftPVP/comments/1kwove8/weird_bug_or_glitch_when_practicing_hit_crystal/)
- [Community discussion about hit-crystal consistency and delay](https://www.reddit.com/r/CompetitiveMinecraft/comments/1jbmyhx/what_am_i_doing_wrong_in_hitcrystalling/)
- [Fabric 1.21.9/1.21.10 entity rendering migration](https://fabricmc.net/2025/09/23/1219.html)
- [Fabric client entity lifecycle events](https://maven.fabricmc.net/docs/fabric-api-0.102.0%2B1.21/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientEntityEvents.html)
- [Yarn 1.21.11 `ClientPlayerInteractionManager`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/client/network/ClientPlayerInteractionManager.html)
- [Yarn 1.21.11 `KeyBinding`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/client/option/KeyBinding.html)
- [Yarn 1.21.11 `Keyboard`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/client/Keyboard.html)
- [Yarn 1.21.11 `Mouse`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/client/Mouse.html)
- [Marlow's Crystal Optimizer v1.1.0 source (MIT)](https://github.com/Bram1903/MarlowsCrystalOptimizer/tree/v1.1.0)
- [Fabric Loader mod discovery API](https://maven.fabricmc.net/docs/fabric-loader-0.17.2/net/fabricmc/loader/api/FabricLoader.html)
- [Fabric Loader mod-container resource API](https://maven.fabricmc.net/docs/fabric-loader-0.17.2/net/fabricmc/loader/api/ModContainer.html)
- [Fabric mixin registration and configuration](https://wiki.fabricmc.net/tutorial%3Amixin_registration)
- [Public KoHs Crystal Tweaks project page](https://modrinth.com/mod/kohs-crystal-tweaks)

## Code findings

1. In 1.21.10/1.21.11, prediction ran from `UseBlockCallback` before the final interaction result was known.
2. The adaptive timeout started at 4 ticks even though configuration declared 12. At latency above roughly 200 ms, prediction could expire before pairing and never learn that latency.
3. The 1.21.x validation accepted crying obsidian even though vanilla `EndCrystalItem` accepts only obsidian or bedrock.
4. The 1.21.x tint implementation queued several parts while temporarily changing `ModelPart.visible`. Rendering happened later, so restoring visibility before queue consumption could duplicate subtrees or mix colors.
5. In 26.1.2, the UI persisted tint/spin/flotation/static values but renderer hooks did not consume them.
6. In 1.21.11, attacking a predicted local crystal removed that prediction and raycast past it. When the matching server crystal had not loaded yet, vanilla had no real entity ID to attack, so the click was lost and the later server crystal remained alive.
7. The earlier integrated optimizer detected the attacked crystal through `crosshairTarget`. After client-side removal it did not retrace the crosshair, so the next rapid use could still target the dead entity and break the place/attack cycle. Marlow instead resolves the crystal by the outgoing packet ID and retraces immediately after cleanup.
8. Keyboard and mouse presses both increment vanilla `KeyBinding` counters. A keyboard binding is therefore not a separate placement action and does not need a separate packet path.
9. `MinecraftClient.handleInputEvents` drains the complete attack counter before the use counter. A physical `use → attack → use` sequence received inside one client tick is therefore reordered, regardless of whether Use Item came from a keyboard binding or the right mouse button.
10. A successful local placement is added after `interactBlock` returns, but the crosshair is normally refreshed later. Without an immediate target update, a following attack in the same input pass still sees the earlier block hit.
11. A confirmed 1.21.11 beta.5 startup log with `marlowcrystal` failed in `PayloadTypeRegistryImpl.register`: both entrypoints registered `marlowcrystal:opt_out`, and Fabric raised `Packet type ... is already registered` before the title screen.
12. A Fabric dependency-level `breaks` declaration cannot provide the requested in-game explanation because Loader would stop before Minecraft creates a screen.
13. `SafeCrystalMixin` targets only `ClientPlayerInteractionManager.attackBlock` and `updateBlockBreakingProgress`. It does not inject into the crystal placement path (`interactBlock`) or crystal entity attacks (`attackEntity`), although disabling it is useful for isolating rapid cycles where the crosshair still points at the base.

## Implemented solution

- Capture the used item in `interactBlock` / `useItemOn` and act only on accepted results.
- Keep the freshly predicted obsidian position for no more than 4 ticks.
- Retarget only if the original crystal hit is invalid and belongs to the same rapid placement sequence.
- Reuse the vanilla packet path; never invoke networking manually.
- Register crystal model parts by identity and select their color in `ModelPart.render`, when queued geometry is actually consumed.
- Bound the configuration UI to current logical dimensions for high GUI scales and compact windows.
- In 1.21.11, intercept only the vanilla `attackEntity` call after vanilla validation. Queue one boolean attack intent on the local prediction, consume it once when the matching server entity loads, and expire it with the prediction.
- Replace fixed option descriptions with hover tooltips so explanatory text does not consume layout space.
- Resolve real-crystal cleanup from the attack packet's entity ID, then clear `targetedEntity` and retrace through KoHs prediction-aware raycasting. This preserves the next physical use without producing another action.
- Record gameplay attack/use arrivals from both vanilla keyboard and mouse callbacks, then drain them in order only when a real `wasPressed()` count exists for that action.
- Target the local crystal immediately after an accepted placement, allowing the next recorded physical attack in the same tick to enter the existing prediction handoff.
- Keep English-first, Spanish-second `Accept` / `Restore` confirmations only for `Local Crystal`, `Seamless Mode`, `Placement Fix`, and `Rapid Attack Fix`.
- Initialize incompatibility detection from an `IMixinConfigPlugin`, then return `false` from `shouldApplyMixin` for every KoHs gameplay mixin when startup is blocked.
- Treat `marlowcrystal` as an explicit incompatibility and stop KoHs client initialization before its mirrored Marlow payloads can be registered.
- For unknown mods, parse Fabric mixin metadata and ASM annotations without loading candidate classes. Block only direct KoHs targets or crystal-related exact target-class/method overlaps with KoHs critical hooks.
- Fail open if generic metadata inspection is malformed, while retaining explicit known-mod detection. This prevents damaged third-party metadata from becoming an unsupported global deny list.
- Replace the config screen with a bounded, scrollable bilingual report; reassert it at client tick end, disable Escape, and expose only `MinecraftClient.scheduleStop()`.
- Expose Safe Crystal as a default-on direct toggle. When disabled, return before player/world/block inspection and do not cancel either vanilla block-breaking method.
- Reflow the six Tweaks controls into two columns on compact logical screens instead of compressing them into the Close-button area.

## Verification boundaries

- All eight published JARs contain readable Fabric metadata matching their documented Minecraft and Java ranges.
- The 1.21.10, 1.21.11, and 26.1.2 projects completed clean Gradle builds for the placement/rendering integration.
- No Minecraft instance was launched. Runtime multiplayer and optional-mod compatibility remain beta testing responsibilities.
- Automated tests verify real KoHs mixin-signature extraction, selector normalization, exact-overlap detection, different-method tolerance, and unrelated-mod tolerance.
- The 26.1.2 custom sound runtime replacement remains incomplete; the port analysis documents the required modern audio path.

## Legitimacy constraints

Placement Fix changes only the `BlockHitResult` consumed by the player's current vanilla interaction. It sends no additional use or attack packets, repeats no input, and performs no remote targeting. The optimizer reacts only to an attack the player already issued and performs client-side visual cleanup.

Rapid Attack Fix does not synthesize clicks, guess entity IDs, or retry packets. It preserves at most one attack that already reached vanilla's normal attack call and sends that single attack only when the corresponding real entity is available.

Ordered Crystal Input does not invoke an action from a held state, remove the four-tick vanilla use cooldown, or manufacture a key count. Every ordered action must consume one count already present in the matching vanilla `KeyBinding`; overflow falls back to untouched vanilla processing.
