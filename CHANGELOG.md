# Changelog

## 2.5.0

### Changed

- **The glow lives in the crystal now.** Its three layers shine, the frame, the inner frame and the
  core, each in its own colour (an unpainted layer in the glow colour, the core a step towards
  white), inside a soft aura in the glow colour. The light is the crystal's own model drawn again
  as additive light, so it keeps the frames' pattern and turns and floats with them. Power,
  reflections and the glow colour work as before.
- **Motion blur**, in the Glow panel: the glowing layers trail behind them as they turn, from a
  short smear to a long one. 50% out of the box; 0% turns it off.

### Added

- **Old KoHs Crystal Glow**, in the Advanced tab: the glow as it was, a halo with rays behind the
  crystal, for your crystals and the enemy's.

## 2.4.1

### Added

- **Instant explosion** (core, always on): the explosion of a crystal you break, its sound (or your
  custom one) and its burst, plays at the hit instead of a round trip later. When the server's
  explosion arrives, its sound and burst are skipped so nothing plays twice; its knockback and
  block debris still come from the server. In the lab, at 100 ms of added latency, the explosion
  is heard 0.2 ms after the hit instead of 133 ms after it.
- **Removal reader** (core, always on): the server's entity removals are read on the network thread
  as they arrive, so a crystal the server removed, anyone's, stops being drawn at once instead of on
  a later frame.
- A gate on the way into the settings: your crystal alone on a wall of stone its glow lights, and
  **Click the crystal to enter**. The click (or Enter, or Space) blows it up: the stone turns to
  obsidian and crying obsidian, the blocks crack and burst, and their pieces rain off to both sides
  while the menu comes up. The local optimization's explanation stands on the same lit stone, and
  its **Continue** sends the crystal to the gate.
- **Advanced** tab: force off, the optimizer benchmark, the Conflict Monitor and Herzium, moved out of
  Crystal Tweaks, which keeps the crystal helpers.
- The open tab shows its name, the others their icons with their names on hover, and a small End
  Crystal hops on the open tab: click it again and the crystal takes the middle back.
- The optimizer window shows Crystal Tweaks and the detected optimizer by their own icons, and its
  title carries your crystal turning in place.
- The benchmark measures **attack → explosion heard**.
- Crystal Lab, the measurement rig behind these numbers: a local server with Grim Anticheat for
  each protocol, Ravenclaw's Ping Equalizer, and a walking obsidian-crystal macro played through the
  player's own keys. On 1.21.11, 26.1 and 26.2 Grim raised no alert in any scenario, and the client
  sent the same packets, cycle for cycle, with the optimizations on as with them forced off. See
  `docs/audits/LEGITIMACY_AUDIT.md`.

### Changed

- A fresh install's crystal is purple all through: its frame, inner frame and core, as its glow
  already was. Colours you already chose are kept; Reset returns to this purple.
- Safe Crystal is now **Don't break obsidian** (**Evitar romper obsidiana**); it does the same and
  keeps its setting.
- Icons are the game's own: the Pigstep disc, the End Crystal, a comparator, a note block, a clock
  and an observer's face; the crystals in the panel and window titles are the real model, turning in
  place. Tabs and icons never show cut at the edges.
- Only real crystal optimizers pause Crystal Tweaks. Mods that do things with crystals, Safe
  Crystal style protections, glows, skins, colours, spins, sizes, sounds, particles and counters,
  never do, whatever their Mixins are called; a Mixin named for attacking, breaking or placing no
  longer counts as an optimizer's. Make My Crystals Faster and Zero Delay Crystals are recognised.
  Checked against the 173 crystal mods on Modrinth: the 18 optimizers are found and nothing else.
- Rows in the panels and drawers scroll clipped to their panel and fade in and out at its edges
  instead of popping, and only the visible part of a half-shown row takes clicks. The wheel moves as
  far as it turns, and the scroll glides.

## 2.4.0

### Added

- A new settings screen built around your crystal. It turns in the centre with every setting on it
  (colours, glow, flash and sound): **Colours** opens on the left, **Glow** on the right, and both
  side panels take on the glow colour, partly, while you change it. Click the crystal to explode
  it and right-click to place it again.
- An intro the first time you open it in a session, when no other crystal optimizer is installed:
  an animated crystal explains the local optimization. It is client-side and legitimate: it only
  changes what you see, adds or changes no packet, and the server still decides every hit and
  placement. **Don't show again** or **Continue**, and the crystal flies to the centre.
- Tabs above the crystal that scroll sideways with the mouse wheel, with a wheel hint the first
  time: **Sound**, **Crystal Tweaks** (ghost crystals, Safe Crystal, obsidian debounce, force off,
  the optimizer benchmark, the Conflict Monitor and Herzium) and **KoHs** (Discord, website,
  Modrinth and Buy me a coffee). The first visit to Crystal Tweaks explains that any crystal
  optimizer can be used with it, and that Crystal Tweaks then turns its own optimization off.
- Tab icons from the game: a small spinning crystal that shifts colour, a glowing one, and a music
  disc for Sound.
- **Enemy crystals** in the top-left corner: your menu shatters and the red enemy menu drops in,
  with **Profile** and **Advanced** tabs, its side panels lit by the enemy glow. Advanced is
  "Coming soon", with a small mining game below it: a crystal with a pickaxe breaks obsidian, then
  bedrock, every 4 to 8 seconds.
- **Crystal Practice** has its own button under the crystal: obsidian pillars with your crystals,
  the warning, and a charge, burst and rift on the way into its setup.

### Changed

- The enemy profile left the Glow tab for its own menu.
- **Reset** on a side panel asks **Sure?** first.
- In a narrow window the side panels become drawers opened from handles at the edges. Escape, or
  a click beside an open one, folds it away.
- The intro replaces the Important notice.

### Fixed

- Minecraft 26.3 renumbered mouse buttons and keys. Crystal Tweaks now reads them by the game's own
  numbers; before, the Crystal Practice kit editor on 26.3 took a left click for a right click.

## 2.3.1

### Fixed

- The crystal glow was sending far more geometry than it drew. A profile of a real fight on 26.2
  put it at about a third of the render thread, almost all of it in writing vertices: every
  triangle went out twice, once per winding, although the material culls back faces and the world
  only ever shows the front one. In the world each triangle now goes out once, turned to face the
  camera; the settings preview, drawn through a mirrored pose, keeps both. The image is unchanged.
- A halo far away, or small on screen, uses a coarser disc: 8 rings by 32 corners from 16 blocks
  and 6 by 24 from 32 blocks, measured at a 70 degree field of view so a zoom brings the full disc
  back. Its outer corners carry almost no light, so the polygon never shows. The spill on the
  ground is sampled every half block instead of every quarter at those distances.
- Together, a live halo sends 52 % fewer vertices up close and 69-83 % fewer further away. With
  30 crystals in view on a 26.2 test client, 98-106 FPS went to 256-279 FPS.

## 2.3.0

### Added

- Flash styles for a destroyed crystal, in the player's own glow colour: the original explosion,
  a skull, your own head, lightning, and ten new shapes: heart, star, shockwave, vortex, crown,
  crescent, snowflake, flower, gem and crossed swords. The shockwave, vortex, star and flower move
  as the flash fades. Drawing only, with no change to the explosion, the damage or any packet.
- A flash size slider, `10%`-`300%`, for every shaped style. The burst sizes itself from the glow
  power, so it does not get one.
- A rebuilt settings screen. Four tabs, **Colors**, **Glow**, **Sound** and **Advanced**, with a
  bar that slides to the selected one; related controls grouped on cards that carry titles when
  there is room; switches with a sliding knob instead of `ON`/`OFF` text; a status chip that shows
  what the optimizer is doing and explains it on hover; rows that fade in one after another; a
  crystal badge and a light that runs round the panel's edge; and an obsidian pedestal under the
  preview, ringed in the glow colour. Its geometry lives in one class the layout tests check
  directly, across more than 360,000 window sizes and tab states.
- The Glow editor is now the **Glow** tab, for your crystals and for the enemy profile alike. The
  flash style has previous and next arrows, and changing it explodes the preview to show it.
- An **Important notice** on the way into the settings, once per session and only when no crystal
  optimizer is installed: Crystal Tweaks optimizes the client only, and Marlow's Crystal Optimizer
  is recommended for placements that reach the server sooner. **Don't show again** turns it off.
- **Safe Crystal** can be switched off in **Advanced**. It stays on by default. It sends fewer
  actions than Vanilla, so a player on a server that forbids input filters can now comply.
- Support for Minecraft 26.3, which needed a real port: it swapped GLFW for SDL and dropped
  `lwjgl-tinyfd` with it, renamed the use-item packet's accessors to record components
  (`hand()`, `hitResult()`, `sequence()`), removed `PoseStack.mulPose(Quaternionf)` and gave
  `EntityRenderer.shouldRender` a trailing `float`. Without a native file dialog on that target, a
  custom sound is chosen by dropping the file into the mod's own sounds folder.
- Performance mods are named explicitly and can never stand this mod down, whatever they overlap.
  Krypton, Lithium, Sodium, C2ME, ImmediatelyFast, ScalableLux, FerriteCore, MoreCulling,
  EntityCulling, ModernFix, LazyDFU, DynamicFPS, MemoryLeakFix, Noxesium, ViaFabricPlus and
  ViaVersion sit on the same network and rendering paths this mod uses, and matching there says
  nothing about crystals.
- **Glow** and **Flash on explosion** each have a switch, and their options fold away while it is
  off. The glow switch only turns off the halo, its reflections and the crystal's own light; the
  flash gets its own size, opacity (`10%`-`100%`) and duration. Saved profiles keep their look.
- Paused crystal helpers in **Advanced** say on hover why they are paused.
- **Obsidian debounce** (off by default): refuses an obsidian placement that follows the previous one
  within the chosen window, Vanilla to `10 s`, whatever key Use is bound to. Changing slot ends it.
- **Force off optimizations**: turns every crystal helper of this mod off. Visuals stay.
- **Advanced optimizer benchmark**: measures real placements and breaks while you play (placed →
  shown, hit → gone, hit → confirmed by the server, next crystal on the same block, FPS) under the
  crystal optimizer in charge, Marlow's and the other known ones included. It only observes and sends
  nothing. A **Normal**/**Dev** switch picks plain explanations or the full statistics.
- **Crystal Practice** (experimental, behind a red warning): a local practice world with a bot, set
  up in three tabs.
  - **Gear**: netherite, diamond or iron armour (Mending, Unbreaking III and Protection IV, Blast
    Protection IV on up to two pieces); a sword with Sharpness V and **Knockback I** or **II**; a
    totem counter from 1 to 20 or a full inventory; and five kit presets, **CPvP standard**,
    **MCTiers style**, **MCPVP style**, **PVPHQ style** and **Light**. None of those ladders publishes
    its crystal kit item by item (MCTiers asks players to bring their own), so each preset follows
    what the ladder does publish and says so.
  - **Arrange inventory** opens the chosen kit in an inventory, like a server's kit editor: a kit
    room to take stacks from, click or drag to move, right click to empty, **Save** and **Reset**.
    The practice kit is handed out exactly as arranged, off hand included, and crystals, obsidian,
    anchors and glowstone refill into the same slots.
  - **Bot**: four difficulties, each adding techniques that cannot be switched off. The tab lists
    them with how each one works: hit-crystal, respawn anchors, face-placing and pearling in on
    Normal; the d-tap timed to the end of the player's damage immunity, the safe anchor behind
    glowstone, top-blocking, hiding in holes and mending on Hard; the butterfly, the double anchor,
    the triple tap, chain pops, crits with W-tap and fall prediction on Extreme. Every bot mines its
    way out with its pickaxe, at Vanilla's mining speed, when it is boxed in. The bot carries the
    player's own kit, totems, apples and pearls included, and is held to human timing: its fastest
    combo is obsidian, crystal and hit on three consecutive ticks.
  - Two **styles**. **Smart** keeps its distance: it blocks off, putting a block in front of itself
    toward the player when threatened and there is ground to set it on, hides in holes and, low on
    health, pearls away to heal instead of hopping backwards. **Aggressive** rushes: it never backs
    off, pearls in from further out, eats without giving ground and accepts a slightly losing trade
    while it holds a totem.
  - Pearls are aimed, not thrown blind: before a throw the bot simulates the flight tick by tick as
    Minecraft moves a pearl (gravity, drag and the first block in its path), over dozens of pitches
    and a few headings, and throws only when it lands on safe ground near the target spot. Paths
    through a crystal, which a pearl would set off, are ruled out.
  - **World**: a flat of netherite blocks, where crystals only go on obsidian someone places; a hole
    arena, an obsidian floor dotted with bedrock-bottomed holes and a few steps; or an almost flat
    natural meadow with few trees and no caves, in one of eight biomes (plains, desert, taiga, snowy
    plains, savanna, cherry grove, badlands and the End). Every death starts a new round on a rebuilt
    arena.
- **Herzium integration**: dimmed without [Herzium](https://modrinth.com/mod/herzium); with it, sets
  Herzium's hotbar order from here. There is no optimizer bridge: Herzium does not touch crystals.
- Built for 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.

### Changed

- Glow now ships on: power and reflections at `55%`, your crystals in crystal purple and enemy
  crystals in red, with the enemy profile enabled. A config file that already exists keeps every
  value in it, so only a fresh install starts this way.
- The Steve head flash is replaced by **My head**, which draws your own skin's face and hat layer as
  a pale, see-through apparition lit in the glow colour. Anyone who had picked Steve gets this
  instead.
- **Lightning** only points at players the player could see when the crystal exploded: not
  invisible, not spectating, inside the view and with a clear line of sight. Its targets are fixed
  at the explosion and every bolt has the same length, so no bolt points at or measures the distance
  to anyone who was not already on screen.
- Turning on the glow's own colour no longer asks for confirmation; it only ever changed the light.
- Sound volume stops at `100%`. Vanilla caps a sound's gain at one, so `200%` was never louder; it
  only let a distant explosion be heard twice as far away.
- Less work per frame with many crystals in view: the glow's discs reuse precomputed angles and the
  flash tracker expires old entries once per frame instead of once per crystal.
- **Reset** on the Glow tab restores the shipped glow instead of switching it off.
- The optimizer detection knows the popular crystal optimizers by their own ids (Marlow's Crystal
  Optimizer, Client Side Crystals, Client-Sided Crystals, HCsCR, FastCrystal, No Crystal Break) and
  the rest by a name that pairs "crystal" with optimizing or client-side handling. It no longer
  stands down for a mod that only says "crystal": FastCrystalSpin was matched for the word "fast",
  and any crystal mod on the send path, a crystals-per-second counter included, was matched for
  the word "crystal". A Mixin overlap now counts only when the overlapping Mixin is named for
  acting on crystals.
- Client Side Crystals now stands the interaction helpers down. It draws a stand-in crystal the
  moment one is placed, as the ghost crystals do, and its stand-in was matched as the player's
  placement, so the real crystal was read as someone else's.
- More performance mods are named so they can never stand the helpers down: PacketFixer,
  BadOptimizations, VMP, Nvidium, Sodium Extra, Iris, GPU Booster, Ixeris and Particle Core.
- Safe Crystal now states its stand-down as one named decision instead of a condition folded into the
  click handler. With Marlow's Crystal Optimizer, No Crystal Break or any other detected crystal
  optimizer installed, the obsidian click reaches Vanilla untouched.

### Fixed

- **Ghost crystals crashed Minecraft 26.2 and 26.3** ("Tried to access entity ID before ID
  assignment"). The stand-in crystal is never added to the world, and from 26.2 an entity without an
  id throws when anything asks for its id or hash code, as the glow cache did. The stand-in now
  carries its own negative id, and the glow cache and the ownership map compare crystals by identity.

- Minecraft 26.3 could not start with the mod installed: the Mixin that hides a predicted break
  still named the old `shouldRender` signature and aborted the game when the crystal renderer
  loaded.
- The settings preview always exploded with the plain burst. It now shows the chosen flash style,
  size and colour.
- The crystal in the settings preview rendered black, and no colour could brighten it. The preview
  render state was never given light coordinates, so it was lit by nothing at all. It now draws at
  full block and sky light, the way vanilla lights a model in a GUI.
- Turning on the custom glow colour repainted the crystal's outer, inner and core layers with it, so
  the picker looked like it had erased the colours underneath and disabling it looked like it
  restored them from nowhere. The custom colour now belongs to the light alone, in both profiles, and
  the settings screen no longer warns that the layers are ignored.
- Lightning bolts read as a row of separate dashes. Every segment was its own quad with its own
  normal, so the two quads meeting at a kink left a notch. A bolt is now one strip whose joints share
  their vertices and mitre their normal.
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
- On 26.2 and 26.3 every glow cut dark discs into the light around it. Those versions gave vanilla's
  dragon-ray material, which the glow is drawn with, the default depth state, so each halo wrote
  depth and hid every light drawn after it: the spill on the ground behind a crystal, other
  crystals' glow and the flash of the next explosion. The glow now uses a copy of that material,
  read from vanilla's own pipeline, with only the depth writes turned off. 1.21.11 and 26.1.x keep
  vanilla's material, which never wrote depth there.
- At high power the glow looked distorted around the crystal. The halo was a flat billboard through
  the crystal's centre, so the turning frames were half in front of it and half behind, and the
  front half cut hard-edged holes into the light that changed every frame. A live crystal's halo is
  now drawn just behind the model, at the same size on screen: the crystal stands in front of its
  own light and the glass shows it through.
- A strong glow or flash was cut off by the ground along a hard straight line that slid as the
  camera moved. Every billboard of light now fades out over the last 0.7 blocks above the crystal's
  base, so it meets the ground already dark.
- With reflections turned up, the light on the ground ended in a hard square at the edge of the
  sampled blocks. It now fades to nothing over the last block.
- The Conflict Monitor drew a found mod's icon as a solid strip down the left of the report,
  covering the first letters of every line, and left the card's icon space empty. The icon was
  drawn with a size where the far corner goes; it now sits in its card.
- For the first moment after the settings opened, the buttons already took clicks but were not
  drawn yet. They now fade in from partly visible, so nothing clickable is ever invisible.
- The settings file is written beside the real one and moved over it, so a crash mid-write can no
  longer leave a broken file that resets every setting on the next launch.

### Removed

- Minecraft 1.21 through 1.21.10. Crystal Tweaks supports 1.21.11 and later: the code only those
  targets used and their matrix rows moved to `archive/`, and `build.gradle` now refuses them with a
  pointer there. Their last release, 2.2.7, stays on Modrinth. The rewrite that briefly brought
  1.21.10 back in this release is kept in `archive/gradle/build-2.3.0.gradle`.

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
