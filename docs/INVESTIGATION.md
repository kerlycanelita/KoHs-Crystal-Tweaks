# Technical investigation and decisions

## External signals

Public hit-crystal reports describe the same pattern: after quickly placing obsidian, the following End Crystal use can retain the previous target and fail to place. Client-side prediction mods also reduce perceived latency by rendering a local entity until the real server entity arrives.

Sources reviewed:

- [Community report: crystal fails to place when spamming after obsidian](https://www.reddit.com/r/MinecraftPVP/comments/1kwove8/weird_bug_or_glitch_when_practicing_hit_crystal/)
- [Community discussion about hit-crystal consistency and delay](https://www.reddit.com/r/CompetitiveMinecraft/comments/1jbmyhx/what_am_i_doing_wrong_in_hitcrystalling/)
- [Fabric 1.21.9/1.21.10 entity rendering migration](https://fabricmc.net/2025/09/23/1219.html)
- [Fabric client entity lifecycle events](https://maven.fabricmc.net/docs/fabric-api-0.102.0%2B1.21/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientEntityEvents.html)
- [Yarn 1.21.11 `ClientPlayerInteractionManager`](https://maven.fabricmc.net/docs/yarn-1.21.11%2Bbuild.1/net/minecraft/client/network/ClientPlayerInteractionManager.html)
- [Marlow's Crystal Optimizer v1.1.0 source (MIT)](https://github.com/Bram1903/MarlowsCrystalOptimizer/tree/v1.1.0)
- [Public KoHs Crystal Tweaks project page](https://modrinth.com/mod/kohs-crystal-tweaks)

## Code findings

1. In 1.21.10/1.21.11, prediction ran from `UseBlockCallback` before the final interaction result was known.
2. The adaptive timeout started at 4 ticks even though configuration declared 12. At latency above roughly 200 ms, prediction could expire before pairing and never learn that latency.
3. The 1.21.x validation accepted crying obsidian even though vanilla `EndCrystalItem` accepts only obsidian or bedrock.
4. The 1.21.x tint implementation queued several parts while temporarily changing `ModelPart.visible`. Rendering happened later, so restoring visibility before queue consumption could duplicate subtrees or mix colors.
5. In 26.1.2, the UI persisted tint/spin/flotation/static values but renderer hooks did not consume them.
6. In 1.21.11, attacking a predicted local crystal removed that prediction and raycast past it. When the matching server crystal had not loaded yet, vanilla had no real entity ID to attack, so the click was lost and the later server crystal remained alive.
7. The earlier integrated optimizer detected the attacked crystal through `crosshairTarget`. After client-side removal it did not retrace the crosshair, so the next rapid use could still target the dead entity and break the place/attack cycle. Marlow instead resolves the crystal by the outgoing packet ID and retraces immediately after cleanup.

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
- Require an English-first, Spanish-second `Accept` / `Restore` confirmation before any toggle is disabled.

## Verification boundaries

- All eight published JARs contain readable Fabric metadata matching their documented Minecraft and Java ranges.
- The 1.21.10, 1.21.11, and 26.1.2 projects completed clean Gradle builds for the placement/rendering integration.
- No Minecraft instance was launched. Runtime multiplayer and optional-mod compatibility remain beta testing responsibilities.
- The 26.1.2 custom sound runtime replacement remains incomplete; the port analysis documents the required modern audio path.

## Legitimacy constraints

Placement Fix changes only the `BlockHitResult` consumed by the player's current vanilla interaction. It sends no additional use or attack packets, repeats no input, and performs no remote targeting. The optimizer reacts only to an attack the player already issued and performs client-side visual cleanup.

Rapid Attack Fix does not synthesize clicks, guess entity IDs, or retry packets. It preserves at most one attack that already reached vanilla's normal attack call and sends that single attack only when the corresponding real entity is available.
