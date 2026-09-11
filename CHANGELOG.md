# Changelog

## 2.3.0

### Changed

- Safe Crystal now states its stand-down as one named decision instead of a condition folded into the
  click handler. With Marlow's Crystal Optimizer, No Crystal Break or any other detected crystal
  optimizer installed, the obsidian click reaches Vanilla untouched: that mod owns the crystal click,
  and two mods deciding whether the same swing mines a block is how a player ends up unable to break
  obsidian at all.
- Performance mods are named explicitly and can never stand this mod down, whatever they overlap.
  Krypton, Lithium, Sodium, C2ME, ImmediatelyFast, ScalableLux, FerriteCore, MoreCulling,
  EntityCulling, ModernFix, LazyDFU, DynamicFPS, MemoryLeakFix, Noxesium, ViaFabricPlus and
  ViaVersion sit on the same network and rendering paths this mod uses, and matching there says
  nothing about crystals.

### Added

- Flash styles for a destroyed crystal, in the player's own glow colour: the original explosion plus
  a skull, a Steve head and bolts thrown toward the players around the blast. Selected in the **Glow
  editor**, beside the power, reflections and colour that drive it; drawing only, with no change to
  the explosion, the damage or any packet.
- Published for 1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2.

### Fixed

- Your own crystals were drawn with the enemy profile while spamming. Each base remembered a single
  placement, so placing, breaking and placing again on the same obsidian produced two crystals
  against one record: the first consumed it and the second was read as someone else's. Attempts are
  now queued per base and claimed oldest first, so every arrival matches the placement that caused
  it.
- The death flash's ground light tore apart as it faded. The surviving-surface filter was written
  back into the stored snapshot every frame, so each block the blast destroyed permanently removed
  its patch of light, and a chunk that reloaded mid-fade never got it back. The filter now applies to
  the frame being drawn and leaves the snapshot intact.
- The same explosion lit the ground only sometimes during a fight. When the per-tick terrain sampling
  budget ran out, a crystal reported no lit surfaces at all and that empty answer was captured into
  the flash. It now reuses its last result instead.

## 2.2.11

### Fixed

- The native optimizer no longer stays off when the compatibility scan cannot read every mod. 2.2.10
  treated an unreadable JAR anywhere in the pack as grounds to disable break prediction, ghost
  crystals, Safe Crystal and placement tracking for the whole session. Only a mod actually found to
  be optimizing crystals turns them off now; an incomplete scan says so in the settings screen and
  leaves the helpers running.
- A mod that merely shares `Connection.send` is no longer mistaken for a crystal optimizer. The
  stand-down test matched on the name of Crystal Tweaks' own Mixin, so protocol translators, network
  performance mods and ping readouts all disabled the optimizer. The overlapping mod now has to be
  about crystals, by its id, its name or the Mixin doing the overlapping.
- A failed metadata read no longer skips the Mixin scan; it falls through to it instead of ending
  detection.

### Added

- **Re-check compatibility** in Advanced Tweaks re-runs detection without restarting the game, for
  when the conflicting mod has just been removed. The tab and the ghost crystal control follow the
  new result as soon as it lands.
- The retired `kohs_crystal_tweaks` is now a known optimizer id, so the notice names the JAR to
  remove instead of leaving the cause unexplained.

## 2.2.10

- Improved detection of Marlow's Crystal Optimizer and No Crystal Break before gameplay.
- Restored normal Mixin priority and skipped prediction bookkeeping when another optimizer is detected.
- Rechecked queued attack predictions after returning to the client thread.
- Keep all interaction helpers off until a clean compatibility scan completes, and off if the scan fails or is incomplete.
- Guard both ghost rendering and prediction state against accidental activation during a conflict.
- Serialized conflict shutdown with client prediction updates and cleared pending placement samples without resetting visual ownership.
- Stopped placement latency confirmation and cleanup while the interaction helpers are disabled.
- Retained the 2.2.9 Glow editor, afterglow, other-player appearances, colors, sounds and preview.
- Published exact-target Fabric builds for 1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2.

## 2.2.7

### Fixed

- Fixed crystals that stayed invisible on the client while the server still tracked them, which
  silently rejected every later placement on the same base. The local instant-break prediction now
  runs only when the crystal is inside the interaction range, the player is alive and not
  spectating, and the crystal is not already removed.
- Fixed the crosshair refresh that runs after a predicted break. It used a block-only ray cast, so
  it never saw entities: with a second crystal in the line of sight it targeted the block behind it,
  and it cleared `crosshairPickEntity` unconditionally. It now re-runs Vanilla's own pick, which
  handles blocks and entities with Vanilla's own ranges.
- Added the missing main-thread guard to the attack prediction. It now hops to the client thread the
  same way the placement observer already did, instead of touching client entity storage from
  whichever thread handed the packet to the connection.
- Every JAR now declares the Fabric Loader version it was actually built against. All targets except
  26.2 previously shipped a `>=0.19.3` requirement that their Minecraft version never had a release
  for, which blocked the mod from loading.
- Every JAR now declares exactly one Minecraft version in `fabric.mod.json`, preventing Modrinth
  from detecting a single build as compatible with the full 1.21-to-1.21.11 range.

### Changed

- Instant crystal break is core behaviour with no setting. Advanced Tweaks is unchanged from 2.2.6
  and still holds only the Conflict Monitor. A `gameplay` section written by a 2.2.7 pre-release is
  removed from the config on the next save.

### Build

- Added `gradle/versions.properties`, a single build matrix holding the Fabric Loader, Fabric API,
  Mod Menu and Java release for every supported Minecraft version. A build is now `-Pmc=<version>`
  instead of five separate flags.
- Added `tools/build-all.ps1`, which builds every target in the matrix, collects the JARs and
  regenerates `CHECKSUMS.sha256`.
- Added `tools/verify-jars.ps1`, which checks each JAR's declared dependencies against its build
  metadata and confirms every class named in the Mixin config is present in that JAR. Mixin does not
  validate `@Invoker` and `@Accessor` targets at compile time, so a per-version class exclusion that
  is not matched by a config exclusion compiles cleanly and then aborts the game at startup.
- Fixed stale generated sources and a stale Mixin config when building several Minecraft versions in
  a row. The per-version source and config transforms were invisible to Gradle's up-to-date checks,
  so a target could be built from the previous target's rewritten sources.
- `CHECKSUMS.sha256` is now written as UTF-8 without a BOM and with LF endings, so `sha256sum -c`
  works on Linux and macOS.

## 2.2.6

- Removed the custom white and portal particle pulse emitted after local crystal placement attempts.
- Kept the silent placement tracker, core behavior, visuals, sounds, and Conflict Monitor unchanged.

## 2.2.5

- Replaced the Advanced Tweaks placeholders with the local Conflict Monitor.
- Added conservative matching for exact Mixin target class and method overlaps, with mod icon, name, author, version, and a qualified impact explanation.
- Removed the former placement-ready and latency overlay messages while retaining silent core tracking.
- Enabled the local post-Vanilla placement observer consistently from Minecraft 1.21 through 26.2.
- Kept the Conflict Monitor and placement feedback local-only: they do not add, cancel, reorder, or mutate gameplay packets.
- Added a documented multiplayer trust-boundary audit for the new functionality.

## 2.2.4

- Changed the mod metadata and public documentation to English.
- Localized the configuration screen to Spanish for every `es` Minecraft locale and to English for every other locale.
- Renamed the Tweaks tab to Advanced Tweaks (`Ajustes avanzados` in Spanish).
- Added configured custom audio to preview explosions, including volume and playback-speed settings.
- Added a vanilla explosion-sound fallback when no custom sound is active.
- Corrected the GUI panel corners so the border and translucent fill share the same rounded pixel contour.
- Kept the optimized core, responsive GUI, preview practice, particles, and animated snake from 2.2.3.
