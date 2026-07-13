# Porting analysis: 1.21.11 to 26.1.2

## Baseline

The 26.1.2 module uses official, unobfuscated Minecraft names. Yarn mappings,
`fabric-loom`, `modImplementation`, and remap tasks are not valid migration
targets for this release.

Selected toolchain:

- Minecraft 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.153.0+26.1.2
- Loom 1.17.12 through `1.17-SNAPSHOT`
- Gradle 9.5.1
- Java 25
- Mod Menu 18.0.0-beta.1

## Completed first milestone

The following 1.21.11 behavior is available and compiling:

- JSON configuration and validation
- Optimization, Visuals, Tweaks and Sound tabs
- Local Crystal and Seamless settings
- frame/core tint selector
- Static Crystal, flotation and spin speed settings
- custom sound toggle, file import, volume and speed settings
- Mod Menu config-screen factory
- client-side crystal prediction
- seamless render-state handoff
- local-crystal attack/use pass-through
- Safe Crystal block protection

The GUI is a rewrite, not a mapping-only port:

| 1.21.11 | 26.1.2 |
| --- | --- |
| `DrawContext` | `GuiGraphicsExtractor` |
| `renderBackground` | `extractBackground` |
| `render` | `extractRenderState` |
| `ButtonWidget` | `Button` |
| `ClickableWidget` | `AbstractWidget` |
| `SliderWidget` | `AbstractSliderButton` |
| `Text` | `Component` |
| `Click` | `MouseButtonEvent` |
| `MinecraftClient` | `Minecraft` |

The panel, tab widths, content bounds, controls and color picker are derived
from the current logical screen size. Compact layouts reduce padding,
descriptions and control height before anything can leave the panel.

## Subsystem decisions

### 1. Pure state and compatibility helpers — low risk

`SeamlessCrystalBridge`, `OptOutCache`, and
`CrystalAnchorCounterCompat` contain no version-sensitive rendering logic.
Port them almost directly. Keep reflection compatibility isolated and optional.

### 2. Client-side crystal prediction — implemented

Core type mapping:

| 1.21.11 | 26.1.2 |
| --- | --- |
| `ClientWorld` | `ClientLevel` |
| `EndCrystalEntity` | `EndCrystal` |
| `Vec3d` | `Vec3` |
| `Box` | `AABB` |
| `RaycastContext` | `ClipContext` |
| `ClientPlayerInteractionManager` | `MultiPlayerGameMode` |

The placement and pairing algorithm now uses position and entity-id indexes.
The old age workaround was removed: 26.1.2 exposes `EndCrystal.time`, and
`EndCrystalRenderer.extractRenderState` copies it to
`EntityRenderState.ageInTicks`.

Implemented hooks:

- Fabric lifecycle and entity load/unload events maintain the prediction state;
- prediction is created only after `MultiPlayerGameMode.useItemOn` returns
  success, so it cannot block vanilla placement validation;
- local entities are tagged and indexed for constant-time identification;
- only one nearest prediction can pair with a server crystal;
- no extra placement or attack packets are sent.

### 3. Crystal animation, tint and seamless transition — implemented

Exact vanilla changes:

| 1.21.11 | 26.1.2 |
| --- | --- |
| `EndCrystalEntityRenderer` | `EndCrystalRenderer` |
| `EndCrystalEntityModel` | `EndCrystalModel` |
| `updateRenderState` | `extractRenderState` |
| `render` | `submit` |
| `MatrixStack` | `PoseStack` |
| `OrderedRenderCommandQueue` | `SubmitNodeCollector` |
| `getYOffset` | `getY` |
| `setAngles` | `setupAnim` |

Completed:

1. `EndCrystalRenderer.extractRenderState` applies the seamless age delta and hidden flag.
2. `EndCrystalRenderer.submit` is cancelled while a paired real crystal must stay hidden.
3. `EndCrystalModel.setupAnim` rebuilds transforms for static, flotation and spin options.
4. The beam uses the same static/flotation decision as the model.
5. Frame/core tint is selected per registered `ModelPart` when queued geometry is actually rendered.

No raw OpenGL calls are used; all rendering remains on Blaze3D's submit pipeline.

### 3.1 Placement Fix — implemented

`MultiPlayerGameMode.useItemOn` records successful predicted obsidian placement and retargets only the next nearby End Crystal use when the original target is invalid. It reuses the original vanilla interaction and does not create packets or automate input.

### 4. Marlow optimizer compatibility — medium/high risk

The old packet replay strategy no longer maps one-to-one. In 26.1.2 attacks
use `ServerboundAttackPacket`, while `ServerboundInteractPacket` is now a
record for non-attack interaction data and has no client-side handler visitor.

Correct route:

- observe or inject `MultiPlayerGameMode.attack` for local crystal removal;
- do not rebuild the removed `PlayerInteractEntityC2SPacket.Handler` pattern;
- keep opt-out/version negotiation as custom Fabric payloads;
- migrate payloads to `CustomPacketPayload`, `FriendlyByteBuf`,
  `StreamCodec`, and `CustomPacketPayload.Type`;
- rename payload registries:
  `configurationC2S/S2C` and `playC2S/S2C` become
  `serverboundConfiguration/clientboundConfiguration` and
  `serverboundPlay/clientboundPlay`.

### 5. Runtime custom sound — high risk

The decoder code for WAV/OGG/MP3 is reusable, but the injection surface moved:

| 1.21.11 | 26.1.2 |
| --- | --- |
| `SoundLoader.loadStatic` | `SoundBufferLibrary.getCompleteBuffer` |
| `StaticSound` | `SoundBuffer` |
| `WeightedSoundSet` | `WeighedSoundEvents` |
| `SoundSystem` | `SoundEngine` |
| `SoundManager.sounds` | `SoundManager.registry` |

Recommended implementation:

1. Keep decoded PCM and `AudioFormat` ownership in `CrystalSoundManager`.
2. Intercept only the mod's runtime sound identifier in
   `SoundBufferLibrary.getCompleteBuffer`.
3. Return a completed `SoundBuffer` built from a copied direct buffer.
4. During `SoundManager.apply`, replace the explosion event registration and
   then allow `SoundEngine.reload`.
5. Restore the captured vanilla registration whenever custom sound is disabled
   or invalid.

The menu currently imports and records files, but runtime replacement stays
disabled until this path is implemented and tested.

### 6. Safe crystal protection — implemented

Both `MultiPlayerGameMode.startDestroyBlock` and `continueDestroyBlock` are
guarded. Breaking is stopped when either hand contains an End Crystal and the
target is obsidian or crying obsidian. Stopping an in-progress break also clears
the client crack animation and sends the normal abort behavior.

## Implementation order

1. Marlow payload compatibility and local attack handling.
2. Runtime sound replacement.
3. Compatibility smoke tests with optional external mods.

Every stage should compile and run independently before the next mixin is
enabled in `fabric.mod.json`.
