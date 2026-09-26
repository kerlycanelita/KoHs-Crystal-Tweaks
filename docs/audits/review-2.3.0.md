# 2.3.0 review and the 1.21.10 archival

## Scope

Full review of Crystal Tweaks at `476e9ed` (2.3.0, not yet published), done on
26 September 2026 before archiving Minecraft 1.21 through 1.21.10. It covers the
build matrix, every Mixin, the interaction helpers, rendering, sound, the
configuration screens, the release tooling and the documentation, from four
angles: exact version porting, fair play and anticheat exposure, performance,
and GUI layout.

No Minecraft instance was launched and no frame-time benchmark was recorded.
Every performance figure below is computed from the code, not measured.

## Archival

Support now starts at 1.21.11: 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.
[archive/README.md](../../archive/README.md) lists what moved and how to
rebuild an archived target.

- `src/legacyOld` (1.21 and 1.21.1 only) moved to `archive/src/` unchanged.
- The 1.21–1.21.10 matrix rows moved to `archive/gradle/versions.properties`.
- `build.gradle` lost eight version flags and their source rewrites (607 to
  299 lines); an unchanged copy is `archive/gradle/build-2.3.0.gradle`.
- `build.gradle` refuses an archived target with a message pointing at the
  archive instead of failing to compile.

Evidence that the six maintained targets did not change:

| Check | Result |
| --- | --- |
| Rebuild of `476e9ed` against `CHECKSUMS.sha256` | 6/6 identical: the build is reproducible |
| JARs after the archival against that baseline | 6/6 byte-identical (same SHA-256) |
| Rewritten client sources for 1.21.11, 26.2 and 26.3 | identical, 45/44/44 files |
| `tools/verify-jars.ps1` | 6 checked, 0 problems |
| `tools/test-glow.ps1` | pass, 42,625 layouts |
| `tools/tests/ScreenLayoutTest.java` | 28 layouts, 0 overlaps, 7 that scroll |
| `tools/verify-mixins.py` | 1.21.11–26.2 pass; 26.3 fails, see F1 |
| `-Pmc=1.21.10`, `-Pmc=1.21` | refused with the archive message |

`EntityRenderDispatcherAccessor` is no longer called by anything on 1.21.11;
it only served the archived 1.21–1.21.5 previews. It stays registered so the
1.21.11 JAR remains the one built as 2.3.0. Remove it with the next version bump.

## Findings

| # | Severity | Area | Finding |
| --- | --- | --- | --- |
| F1 | Critical | Porting | 26.3 cannot start: a Mixin targets a method signature 26.3 no longer has (fixed) |
| F2 | High | Fair play | The lightning flash points at every tracked player, including hidden ones (fixed) |
| F3 | Medium | Fair play | `LEGITIMACY_AUDIT.md` describes 2.2.7 behaviour that no longer exists (fixed) |
| F4 | Medium | Fair play | Safe Crystal filters input and cannot be switched off (fixed) |
| F5 | Medium | Performance | The glow rebuilds about 17,000 vertices per crystal per frame |
| F6 | Low | Fair play | A custom sound above 100% doubles how far explosions carry |
| F7 | Low | Performance | Afterglow bookkeeping allocates per crystal per frame |
| F8 | Low | Robustness | The config file is overwritten in place (fixed) |
| F9 | Low | GUI | Invisible buttons during the intro, fixed strings, one 1,605-line screen (partly fixed) |
| F10 | Low | Docs | Stale statements in README, RELEASING, the 2.3.0 notes and the index (fixed) |

### F1 — 26.3 crashes at startup

`EndCrystalRendererMixin` injects into
`shouldRender(EndCrystal, Frustum, double, double, double)Z`. In 26.3 that
method gained a `float`:

```text
26.2  shouldRender(Lnet/.../EndCrystal;Lnet/.../culling/Frustum;DDD)Z
26.3  shouldRender(Lnet/.../EndCrystal;Lnet/.../culling/Frustum;DDDF)Z
```

The config sets `"required": true` and `defaultRequire: 1`, so the injection
finds no target and Mixin aborts the game as soon as `EndCrystalRenderer`
loads, which happens while the entity renderers are registered at startup. The
26.3 JAR in `CHECKSUMS.sha256` has this defect. `verify-mixins.py` reports it;
the compiler cannot, because Mixin resolves the string at runtime.

Fix: a 26.3-only rewrite of that one Mixin that changes the descriptor to
`DDDF)Z` and adds a `float partialTick` handler parameter, followed by
`verify-mixins.py` and a start of the 26.3 test instance. The injection stays at
`HEAD`, so hiding a predicted break keeps the same moment and effect.

### F2 — Lightning bolts reveal hidden players

`CrystalFlashShapes.bolts` walks `minecraft.level.players()` and throws a bolt
toward each player within 24 blocks of the blast. That list holds every player
the server tracks for this client, invisible players and players behind walls
included. The bolt length also grows with distance. Each flash
therefore points at, and roughly ranges, players the player cannot see, which a
server can reasonably treat as ESP. The class comment says the opposite ("only
looks at players the client is already rendering"), and so do the README and the
2.3.0 notes.

Nothing has shipped with it: flash styles are new in 2.3.0, which is not
published. Before publishing, either aim the bolts without reading players, as
the preview already does, or keep only players the local player can legitimately
see (`!isSpectator()`, `!isInvisibleTo(self)`, clear line of sight) and make the
bolt length constant.

### F3 — The legitimacy audit is out of date

`LEGITIMACY_AUDIT.md` still covers 2.2.7: it describes removing the crystal from
the level and refreshing the crosshair. The current code only skips the crystal
while drawing: the entity and crosshair are untouched, and the packets are the
ones Vanilla would send. The audit omits ghost crystals, the ownership colours,
the glow and afterglow, the flash styles and the custom sound. The README links
it as the authority on the client/server boundary, and `RELEASING.md` asks for
an update whenever that boundary moves. Refresh it before publishing 2.3.0.

### F4 — Safe Crystal has no switch

`ObsidianPlacementGuard` returns `FAIL` from `AttackBlockCallback` for obsidian
while a crystal is held, so no `START_DESTROY_BLOCK` is sent. This is the L2
input filter of the 2.2.10 review: fewer packets than Vanilla, no automation.
It turns off only when another crystal optimizer is detected. A player on a
server that forbids input filters cannot disable it. Add a toggle; on by
default keeps today's behaviour.

### F5 — Glow cost per crystal

Computed from `CrystalGlowRenderer` at the default 55% power: the burst draws
three discs of 10 rings × 40 segments, each triangle emitted in both windings,
which is 4,800 vertices per disc and pass, or 14,400. The rays add 384 and a
typical ground spill of 25 surfaces adds 2,400. With power and reflections at
300% the extra passes bring it to about 53,000. The disc loops also call `cos`
and `sin` about 1,600 times per disc and pass. All of it is rebuilt every frame for every glowing crystal within 64
blocks, on the render thread.

Two changes would give the same image for less work, and both need a benchmark
first (for example ten crystals, fixed camera, frame time over 60 seconds, glow
off against glow on):

- a 41-entry sine and cosine table computed once;
- one winding in the world. The disc always faces the camera and `DRAGON_RAYS`
  does not disable culling in 26.2, so the second winding appears to be culled
  outside the mirrored GUI preview. Confirm the pipeline's cull default before
  relying on this, because doubling brightness by drawing both would make the
  change visible.

### F6 — Loud custom sounds carry further

The custom explosion plays at `4 × volume`. Minecraft stretches a sound's
audible distance by its volume above 1, so at the 200% maximum a crystal
explosion is heard across 128 blocks instead of 64, and a blast 50 blocks away
plays at about 61% instead of 22%. The client only hears explosions the server
sent it, but it hears distant ones far more clearly than Vanilla. Capping the
slider at 100% keeps Vanilla's falloff.

### F7 — Afterglow bookkeeping

`CrystalAfterglow.observe` runs for every crystal every frame: it sweeps the
whole `SEEN` map and allocates a render-state snapshot in case the crystal dies
in the next half second. `submit` copies `SEEN` and re-filters surfaces with a
stream each frame. The cost is small next to F5; expiring once per frame and
snapshotting on demand would remove most of it.

### F8 — Config writes

`CrystalVisualConfig.save` writes `crystal_tweaks.json` in place. An interrupted
write leaves invalid JSON; the next load falls back to defaults and the next save
keeps them, so every setting is lost. Write a temporary file and move it over
the original.

### F9 — Configuration screens

What works: panels are bounded (470×255), margins adapt, the preview only
appears when the content area is at least 300×86, scrolled-out widgets are
hidden rather than covered so no invisible hitbox stays live, the screen owns
its background (`extractBackground`, rewritten to `renderBackground` on 1.21.11)
so Vanilla's blur never reaches it, animations are time-based, and the Conflict
Monitor releases its icon textures when it closes.

To improve:

- For the first ~125 ms after opening, `extractRenderState` returns before
  drawing widgets, but they already accept clicks.
- Every string is a Spanish or English literal. There are no language files, so
  resource packs and translators cannot change them.
- `CrystalTweaksScreen` holds layout, theme colours, widgets and preview in
  1,605 lines. The glow editor's own layout class showed the better pattern:
  pure geometry that the tests call directly. `ScreenLayoutTest` still mirrored
  the main screen's rows by hand and could drift from it.
- Animations read `System.currentTimeMillis`, which jumps with the wall clock.

### F10 — Documentation

Corrected together with this review:

- README and the 2.3.0 Modrinth notes still named a Steve head; 2.3.0 replaced
  it with the player's own head.
- `RELEASING.md` referred to `MinecraftPickInvoker`, which no longer exists, to
  an "Instant break" setting that 2.2.7 made permanent, to widening a
  `minecraft` range that is now one exact version, and to
  `docs/LEGITIMACY_AUDIT.md`, which moved to `docs/audits/`.
- `docs/README.md` gave the mod version as 2.2.10.
- The 2.3.0 changelog said it was published. It is on neither Modrinth (latest
  2.2.11, 10 September) nor GitHub Releases (latest 2.2.8).

## Fair play by feature

| Feature | Class | What changes | Packets | Risk |
| --- | --- | --- | --- | --- |
| Break prediction | L1 local prediction | Hit crystal skipped while drawing for 250 ms–1 s | Unchanged | Low; servers that forbid any prediction still apply |
| Ghost crystals | L1 visual, opt-in, 26.x | Stand-in drawn until the server's crystal arrives | Unchanged | Low; off by default |
| Safe Crystal | L2 input filter, switchable | Obsidian not mined while holding a crystal | Fewer | Low since F4 |
| Placement observer | L0 | Reads the sent packet for latency | Unchanged | None |
| Ownership colours | L0 visual heuristic | Your crystals and others' drawn differently | Unchanged | None |
| Glow, reflections, afterglow | L0 cosmetic | Depth-tested light; crystals brighter in the dark | Unchanged | None: nothing shows through walls |
| Flash: explosion, skull, own head | L0 cosmetic | Shape of the death flash | Unchanged | None |
| Flash: lightning | L0 cosmetic with a visibility rule | Bolts toward players already in sight, fixed length | Unchanged | Low since F2 |
| Custom sound | L0 cosmetic | Replaces the crystal explosion locally | Unchanged | Low above 100% (F6) |
| Conflict Monitor and detector | L0 local scan | Reads installed mods at startup | Unchanged | None |

No feature sends, delays, reorders or duplicates a packet, or changes reach,
rotation, cooldown or click rate. A server-only anticheat sees Vanilla traffic
except for the dig packets Safe Crystal withholds.

## Modrinth

Modrinth lists 61 versions built only for 1.21–1.21.10, from `1.0.0+mc1.21` to
2.2.7. Archiving them keeps them downloadable and marks them as unsupported.
`1.0.0` declares both 1.21.10 and 1.21.11 and is left alone. This needs the
owner's approval; nothing was changed on Modrinth.

## Follow-up the same day

At the owner's request, after this review:

- **F1** 26.3 gets its own rewrite of `EndCrystalRendererMixin`: the `DDDF)Z` descriptor and a
  `float partialTick` argument. `verify-mixins.py` now passes on all six targets.
- **F2** Lightning takes its targets once, at the explosion, from
  `CrystalFlashShapes.visibleBoltTargets`: no self, spectator or player invisible to the viewer,
  inside the view cone from the FOV setting, and a clear visual line of sight to the head or body.
  Every bolt has the same reach.
- **F3** `LEGITIMACY_AUDIT.md` rewritten for 2.3.0.
- **F4** Safe Crystal has a switch in Advanced, on by default.
- **F8** The config is written to a temporary file and moved over the real one.
- **F9** The screen was rebuilt around `CrystalScreenLayout`, pure geometry that the screen places
  its widgets from and `ScreenLayoutTest` checks directly across 362,880 window-size and tab-state
  combinations with no problems. Colours live in `CrystalTheme` and drawing in `CrystalUi`. Rows fade
  in from 35% visible, so nothing clickable is ever invisible. Strings are still Spanish and English
  literals.

Also requested and done: ten new flash styles, a paler **My head**, a flash size down to 10%, the
preview explosion showing the chosen style, and a once-per-session notice recommending Marlow's
Crystal Optimizer when no crystal optimizer is installed.

F5, F6 and F7 are unchanged. Nothing was launched in game; the owner tests in their instances.
