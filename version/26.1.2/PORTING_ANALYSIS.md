# Release 2 modern-port analysis

Minecraft 26.x uses official Mojang names, Java 25, Loom 1.17, and the submit-based renderer. The modern source set preserves the stable release behavior while using native 26.x APIs.

## Important API adaptations

| Subsystem | 1.21.11 Yarn | 26.x official names |
|---|---|---|
| Client | `MinecraftClient` | `Minecraft` |
| Interaction | `ClientPlayerInteractionManager` | `MultiPlayerGameMode` |
| Crystal | `EndCrystalEntity` | `EndCrystal` |
| Rendering | queued `render` path | `extractRenderState` / `submit` |
| Sound buffer | `StaticSound` | `SoundBuffer` |
| Sound registry | `WeightedSoundSet` | `WeighedSoundEvents` |
| Networking payload | `CustomPayload` | `CustomPacketPayload` |

## Safety and timing invariants

- Prediction is created only after vanilla accepts a real crystal interaction.
- Placement Fix records only an accepted obsidian placement and can retarget only that exact base or its exact placement offset.
- Ordered Input consumes existing vanilla click counters; it never creates a new action.
- Rapid Attack Fix stores one boolean pending intent and waits for the server-assigned real entity ID.
- Local cleanup reads the entity ID from an already-created vanilla attack packet.
- Runtime sound replacement affects only the registered explosion event and restores the captured vanilla registration when disabled.
- Incompatibility scanning blocks only known optimizers, direct KoHs mutation, or an exact timing-critical class/method overlap.

Minecraft 26.2 moved screen ownership into `Minecraft.gui` and built-in entity constants into `EntityTypes`; its source variant adapts those two changes without altering behavior.
