# Crystal Tweaks Wiki

[Back to README](README.md) · [Modrinth](https://modrinth.com/mod/kohs-crystal-tweaks) · [Discord](https://discord.gg/9t2VxEF7UU) · [Report an issue](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)

Welcome to the Crystal Tweaks documentation. This guide covers installation, configuration, the interactive preview, compatibility, and common troubleshooting steps.

## Contents

- [Installation](#installation)
- [Opening the configuration screen](#opening-the-configuration-screen)
- [Visuals](#visuals)
- [Sounds](#sounds)
- [Advanced Tweaks](#advanced-tweaks)
- [Interactive preview](#interactive-preview)
- [Languages](#languages)
- [Compatibility and multiplayer](#compatibility-and-multiplayer)
- [Troubleshooting](#troubleshooting)
- [Building from source](#building-from-source)
- [Getting support](#getting-support)

## Installation

1. Install the Fabric Loader version required by your Minecraft release.
2. Install the matching Fabric API release.
3. Download the Crystal Tweaks JAR for the exact Minecraft version you use.
4. Place the JAR in the instance's `mods` directory.
5. Optionally install Mod Menu for direct access to the configuration screen.

Do not install two Crystal Tweaks JARs in the same instance. A file made for a different Minecraft version must not remain in the active `mods` directory.

## Opening the configuration screen

With Mod Menu installed:

1. Open **Mods** from the Minecraft menu.
2. Select **Crystal Tweaks**.
3. Press the configuration button.

The interface is designed to remain usable at high GUI scales. Option panels become scrollable when the available height is limited.

## Visuals

The **Visuals** tab controls the appearance and movement of rendered End Crystals.

### Crystal colors

Three independent color controls are available:

- **Outer layer** changes the external rotating frame.
- **Inner layer** changes the secondary rotating frame.
- **Core** changes the central cube.

Colors are applied over the active End Crystal texture. Resource packs that heavily recolor or replace the crystal texture can change the final result, so mostly neutral textures are recommended.

### Rotation speed

The rotation slider ranges from `0%` to `300%`:

- `0%` keeps the rotation static.
- `100%` matches Vanilla speed.
- `300%` is the configured maximum.

### Floating speed

The floating slider uses the same range:

- `0%` stops vertical floating motion.
- `100%` matches Vanilla speed.
- `300%` is the configured maximum.

Both movement settings are represented by the crystal shown in the interactive preview.

## Sounds

The **Sounds** tab controls the local End Crystal explosion sound.

- Supported formats: WAV, OGG, and MP3.
- Recommended maximum duration: five seconds.
- Volume range: `0%` to `200%`.
- Playback-speed range: `0.5x` to `2.0x`.

The selected sound and its volume and speed settings are also used by preview explosions. When no custom sound is active, the preview uses the Vanilla explosion sound.

Sound replacement is local: it changes what the player hears without replacing the server's explosion behavior.

## Advanced Tweaks

The **Advanced Tweaks** tab holds the Conflict Monitor.

The Conflict Monitor scans installed mods locally and lists exact Mixin class-and-method overlaps
with Crystal Tweaks. It reads local files only and never contacts a server. A clean result is not a
guarantee: dynamic Mixin plugins and external bytecode agents are not visible to it.

Instant crystal break is part of the mod's core and has no setting. It only runs for a hit the
server is expected to accept: the crystal must be inside your interaction range, not already
removed, and you must be alive, not spectating, and able to deal damage. Anything outside that waits
for the server exactly like Vanilla.

## Interactive preview

The preview uses the player's currently mapped controls rather than assuming fixed mouse buttons:

- Press the mapped **Attack** control to explode the preview crystal.
- Press the mapped **Use Item** control to place it again.
- If it remains absent, it reappears automatically after approximately three seconds.

The preview is rendered only inside the configuration screen. It does not place a real block or entity in the world.

## Languages

Crystal Tweaks selects its interface language from Minecraft's active language code:

- Language codes beginning with `es` use Spanish.
- Every other language currently uses English as the fallback.

This includes regional Spanish variants such as Spain, Mexico, Argentina, Ecuador, and other `es_*` locales.

## Compatibility and multiplayer

Never run Crystal Tweaks alongside the retired **KoHs Crystal Tweaks** build. The two projects have different mod IDs, so Fabric can otherwise load both simultaneously. They can target overlapping crystal interaction and rendering methods.

Avoid combining it with another mod that modifies:

- End Crystal attack or placement input.
- Client-side crystal prediction.
- Attack/use ordering or replay.
- Hotbar selection for crystal combat.
- The same network or rendering mixins.

A high mixin priority does not disable another mod; both injected callbacks can remain active. Use one crystal interaction optimizer at a time.

Multiplayer policies vary between servers. Check the server's published rules and request staff approval if client-side interaction changes are restricted. No client mod can promise universal acceptance by every anticheat or server.

## Troubleshooting

### Minecraft reports an incompatible version

Verify that the Crystal Tweaks filename matches the exact Minecraft version used by the instance. Also confirm that Fabric Loader, Fabric API, and Java meet the requirements shown in the [README](README.md#requirements).

### The configuration button is missing

Install Mod Menu for your exact Minecraft version and restart the client. Confirm that both Mod Menu and Crystal Tweaks appear in the loaded mod list.

### Colors look different with a resource pack

Crystal Tweaks applies color over the texture supplied by Minecraft or the active resource pack. Try a neutral crystal texture to verify the configured color directly.

### The custom sound does not play

Check that the file is WAV, OGG, or MP3, is no longer than five seconds, and remains accessible in the configured sounds directory. Disable the custom selection to confirm that the Vanilla fallback still plays.

### Controls do not fit on the screen

Scroll inside the active options panel. If necessary, temporarily reduce Minecraft's GUI scale and reopen the screen.

### Crystal behavior is inconsistent

Check the instance's `mods` directory for older Crystal Tweaks builds, KoHs Crystal Tweaks, or another crystal interaction optimizer. Remove conflicts only after closing Minecraft and keeping a backup of the instance.

### Placed crystals do not appear, or a spot refuses new crystals

Up to and including 2.2.6, a crystal you attacked was always hidden locally, even when the server
refused the hit. The server kept the crystal, your client did not, and every later placement on that
obsidian was silently rejected with nothing shown on screen.

Update to 2.2.7, which only predicts hits the server is expected to accept. Relogging, or moving far
enough for the chunk to reload, clears an already-desynchronised crystal.

### Placing feels delayed on a high-ping server

A placed crystal is created by the server, so Vanilla cannot show it before one full round trip.
Crystal Tweaks does not change placement timing, click rate or the Vanilla use cooldown, so some
delay is expected and is not a bug.

### Placing right after breaking swallows the click

This is fixed in 2.2.7. Breaking a crystal invalidates the crosshair target Minecraft computed at
the start of the tick; if it is not refreshed, the very next placement takes Vanilla's entity branch
against the crystal that is already gone, and is discarded while still spending the four-tick use
cooldown, so roughly 200 ms is lost with nothing shown.

It is most noticeable with Attack and Use bound to separate keys or mouse buttons, because both can
fire inside the same tick.

## Building from source

Pick a Minecraft target with `-Pmc`; the Fabric Loader, Fabric API, Mod Menu and Java release come
from `gradle/versions.properties`:

```powershell
.\gradlew.bat build -Pmc=26.1.2
```

The generated development artifact is placed under `build/libs`. Released version-specific artifacts
are collected in `versions`. See `docs/RELEASING.md` for building the whole matrix at once.

## Getting support

Before requesting help, include the Minecraft version, Fabric Loader version, Fabric API version, Crystal Tweaks filename, and the relevant client log.

- [Report a reproducible problem on GitHub](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)
- [Join the Crystal Tweaks Discord server](https://discord.gg/9t2VxEF7UU)
- [Download official releases from Modrinth](https://modrinth.com/mod/kohs-crystal-tweaks)
