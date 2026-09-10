# Changelog

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
