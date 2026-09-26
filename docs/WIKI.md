# Crystal Tweaks Wiki

[Back to README](../README.md) · [Modrinth](https://modrinth.com/mod/kohs-crystal-tweaks) · [Discord](https://discord.gg/9t2VxEF7UU) · [Report an issue](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)

Welcome to the Crystal Tweaks documentation. This guide covers installation, configuration, the interactive preview, compatibility, and common troubleshooting steps.

## Contents

- [Installation](#installation)
- [Opening the configuration screen](#opening-the-configuration-screen)
- [Client or server](#client-or-server)
- [Colors](#colors)
- [Glow](#glow)
- [Sound](#sound)
- [Advanced](#advanced)
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

The first time you open it in a session, and only if no crystal optimizer is installed, an
**Important notice** explains what Crystal Tweaks does and does not speed up (see
[Client or server](#client-or-server)). **Got it** continues to the settings; **Don't show again**
turns the notice off for good.

The screen has four tabs: **Colors**, **Glow**, **Sound** and **Advanced**. Related controls sit
together on cards, and a live crystal preview stands on the right when the window is wide enough.
The chip in the top-right corner shows the optimizer's state; hover it for the details. On a small
window the cards lose their titles and the options scroll, so every control stays reachable at any
GUI scale.

## Client or server

Crystal Tweaks optimizes the client only. A crystal you break disappears on your screen straight
away, but what reaches the server is exactly what Vanilla would send, so your next crystal is not
placed any sooner.

If you want the improvement to reach the server too, Marlow's Crystal Optimizer is recommended.
Check your server's rules first. When it is installed, Crystal Tweaks detects it and turns all of
its own optimizations off so the two never fight; colours, glow, flashes and sounds keep working.

## Colors

The **Colors** tab controls the appearance and movement of rendered End Crystals.

### Crystal colors

Pick a layer, then set its colour with the picker or a hex code:

- **Outer** changes the external rotating frame.
- **Inner** changes the secondary rotating frame.
- **Core** changes the central cube.

Colors are applied over the active End Crystal texture. Resource packs that heavily recolor or
replace the crystal texture can change the final result, so mostly neutral textures are recommended.

### Rotation and floating speed

Both sliders range from `0%` to `300%`: `0%` stops the movement, `100%` matches Vanilla and `300%`
is the maximum. The preview crystal follows them.

### Enemy crystals

**Enemy crystals…** opens the same Colors and Glow tabs for crystals you did not place. The server
does not say who placed a crystal, so this is an approximation: a crystal that appears where you just
placed one is yours, and every other crystal uses the enemy profile. **Use this profile** turns the
enemy look on or off.

## Glow

The **Glow** tab owns everything the crystal's light does: power, reflections, the flash style and
its size, and the glow colour.

- **Power** and **Reflections** range from `0%` to `300%`. Reflections are a coloured light drawn
  on the tops of nearby blocks; world lighting is not changed.
- **Own colour** gives the glow and the flash a colour of their own. The crystal's layer colours are
  never changed by it.

**Flash style** is the shape a crystal leaves behind when it explodes. Use the arrows or click the
name to change it; the preview explodes on its own to show the new shape. It takes the glow colour
and material and needs power above `0%`:

| Style | Look |
| --- | --- |
| Explosion | The original burst of stacked discs and rotating facet rays. |
| Skull | A skull silhouette with hollow sockets, nose and teeth. |
| My head | Your own skin's face, pale and see-through, with its hat layer. |
| Lightning | Jagged bolts thrown toward the players you can see near the blast. |
| Heart | A pixel heart with a highlight. |
| Star | A five-pointed star that turns as it fades. |
| Shockwave | A ring that runs outward as the flash fades. |
| Vortex | Three arms spiralling out of the blast. |
| Crown | A pixel crown with two jewels. |
| Crescent | A crescent moon with two small stars. |
| Snowflake | Six branched arms. |
| Flower | Six petals around a bright heart. |
| Gem | A faceted diamond. |
| Swords | Two swords crossed at the middle of their blades. |

**Flash size** scales every shaped style from `10%` to `300%`; the Explosion burst sizes itself from
the glow power.

Lightning only ever points at players you could see when the crystal exploded: not invisible, not
spectating, in front of you and not behind a wall. Every bolt has the same length, so none of them
measures out where a player is. With nobody in sight it draws a ring.

Every style is drawing and nothing else. None of them change the explosion, its damage, its radius or
its sound. Because they are visual, they keep working while another optimizer has the interaction
helpers paused.

## Sound

The **Sound** tab controls the local End Crystal explosion sound.

- Supported formats: WAV, OGG, and MP3.
- Maximum duration: five seconds.
- Volume range: `0%` to `200%`. Above `100%` a distant explosion is also heard further away than
  with Vanilla.
- Playback-speed range: `0.5x` to `2.0x`.

On Minecraft 26.3, which ships no native file dialog, drop the file into the mod's sounds folder and
press **Select file…** again; the status line says where the folder is.

The selected sound and its volume and speed settings are also used by preview explosions. When no
custom sound is active, the preview uses the Vanilla explosion sound. Sound replacement is local: it
changes what you hear without changing the server's explosion.

## Advanced

The **Advanced** tab holds the crystal helpers and the compatibility tools.

- **Ghost crystals** (26.1 and later, off by default) draws a stand-in crystal while the server's
  real one is in flight. It is only a drawing: it cannot be hit, looked at or collided with.
- **Safe Crystal** (on by default) keeps you from mining the obsidian under your crystals while you
  hold one. It sends fewer actions than Vanilla, so switch it off if your server forbids input
  filters.
- **Conflict Monitor** scans installed mods locally and lists exact Mixin class-and-method overlaps
  with Crystal Tweaks. It reads local files only. A clean result is not a guarantee: dynamic Mixin
  plugins and external bytecode agents are not visible to it.
- **Re-check compatibility** runs the optimizer detection again without restarting the game.

The detection stands the interaction helpers down only for a mod actually found to be optimizing
crystals: one that names itself an optimizer, or one whose Mixins land on the same interaction path
*and* whose id, name or Mixin is about crystals. Sharing a network method is not enough on its own,
and a scan that cannot read every mod says so instead of disabling anything.

**What a detected optimizer turns off.** Every interaction helper, with no exception: break
prediction, ghost crystals, placement tracking and Safe Crystal. Safe Crystal matters most here: it
decides what the obsidian click does, and the other optimizer wants that same click; with both
deciding, the usual result is obsidian you cannot break at all. Every visual feature keeps working.

**What never turns anything off.** Performance mods. Krypton, Lithium, Sodium, C2ME, ImmediatelyFast,
ScalableLux, FerriteCore, MoreCulling, EntityCulling, ModernFix, LazyDFU, DynamicFPS, MemoryLeakFix,
Noxesium, ViaFabricPlus and ViaVersion are named in the detector so that no Mixin overlap can be read
as a rival crystal optimizer.

Instant crystal break is part of the mod's core and has no setting. It only hides a crystal you hit
when the server is expected to accept the hit: the crystal must be inside your interaction range,
not already removed, and you must be alive, not spectating, and able to deal damage. The crystal
stays in the world; it is only not drawn until the server confirms the break.

## Interactive preview

The preview uses your mapped controls rather than fixed mouse buttons:

- Click the preview, or press the mapped **Attack** control, to explode the crystal.
- Press the mapped **Use Item** control to place it again.
- If it remains absent, it reappears automatically after about three seconds.

The explosion shows your chosen flash style, size and colour. The preview is rendered only inside
the configuration screen and never places a real block or entity.

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

Verify that the Crystal Tweaks filename matches the exact Minecraft version used by the instance. Also confirm that Fabric Loader, Fabric API, and Java meet the requirements shown in the [README](../README.md#requirements).

### The configuration button is missing

Install Mod Menu for your exact Minecraft version and restart the client. Confirm that both Mod Menu and Crystal Tweaks appear in the loaded mod list.

### Colors look different with a resource pack

Crystal Tweaks applies color over the texture supplied by Minecraft or the active resource pack. Try a neutral crystal texture to verify the configured color directly.

### The custom sound does not play

Check that the file is WAV, OGG, or MP3, is no longer than five seconds, and remains accessible in the configured sounds directory. Disable the custom selection to confirm that the Vanilla fallback still plays.

### Controls do not fit on the screen

Scroll inside the active options panel. If necessary, temporarily reduce Minecraft's GUI scale and reopen the screen.

### The optimizer does nothing

Open the configuration screen and look at the chip in the top-right corner; hover it for details:

- **Paused** — another mod is already optimizing crystals, and Crystal Tweaks has yielded the
  interaction path to it on purpose. Remove it and press **Re-check compatibility** in Advanced, or
  keep it and use its own optimizer.
- **Checking…** — the compatibility scan is still running. It finishes in the first seconds after
  the client starts.
- **Active · partial** — a mod could not be read. The optimizer is running anyway; `latest.log`
  names the mod.
- **Client active** — the helpers are running. If the break still feels like Vanilla, confirm the
  hit qualifies for prediction: inside your interaction range, crystal not already removed, and you
  alive, not spectating and able to deal damage.

Before 2.2.11 an unreadable JAR anywhere in the pack, or any mod sharing `Connection.send`, was
enough to keep every helper off for the session. Updating resolves that case.

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

### Placing right after breaking goes to the old crystal

That is Vanilla's timing, kept on purpose. A crystal you hit is only hidden: until the server
confirms the break it is still in the world, and your crosshair still rests on it, exactly as it
would without the mod. A placement in that instant is sent at the crystal, as Vanilla sends it.

2.2.7 removed the crystal from the world instead and refreshed the crosshair, which let the next
placement reach the obsidian sooner. That changed which packet the client sent, so later versions
went back to Vanilla's. If you want placements to reach the server sooner, see
[Client or server](#client-or-server).

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
