# Crystal Tweaks 2.3.0 - Fourteen flashes, a brand-new settings screen and Crystal Practice

## New

- **Flash styles.** The flash a crystal leaves when it explodes can now take a shape, in your own glow colour: the original **explosion**, a **skull**, **your own head** as a pale ghost, **lightning**, and ten new ones: **heart**, **star**, **shockwave**, **vortex**, **crown**, **crescent**, **snowflake**, **flower**, **gem** and **crossed swords**. The shockwave, vortex, star and flower move as the flash fades.
- **Flash size** from `10%` to `300%` for every shaped style.
- **A new settings screen.** Four tabs (**Colors**, **Glow**, **Sound**, **Advanced**), controls grouped on cards, switches with sliding knobs, a status chip that tells you what the optimizer is doing, animated tabs and rows, and an obsidian pedestal under the preview crystal. Click the preview to see your flash; changing the flash style shows it straight away.
- **Safe Crystal can be switched off** in Advanced, for servers that do not allow input filters. It stays on by default.
- **Better crystal optimizer detection.** Marlow's Crystal Optimizer, Client Side Crystals, Client-Sided Crystals, HCsCR, FastCrystal, No Crystal Break and the many "... Crystal Optimizer" mods are recognized, and Crystal Tweaks steps its interaction helpers aside for them. Mods that only say "crystal" no longer do that: End Crystal skins, spin and size tweaks, crystals-per-second counters and PvP clients keep every helper running, and performance mods such as Krypton, Sodium or Lithium never count.
- An **important notice** the first time you open the settings in a session, only when no crystal optimizer is installed: Crystal Tweaks optimizes your client only; for placements that reach the server sooner, Marlow's Crystal Optimizer is recommended. You can turn the notice off for good.
- **Glow and Flash on explosion switches.** Each has its own switch, and its options fold away while it is off. The flash gets its own **size**, **opacity** (`10%`-`100%`) and **duration**.
- **Paused crystal helpers say why** on hover in Advanced.
- **Obsidian debounce** (off by default): refuses an obsidian placement that follows the previous one within the window you choose, Vanilla to `10 s`, whatever key Use is bound to.
- **Force off optimizations** turns every crystal helper of this mod off. Visuals stay.
- **Advanced optimizer benchmark:** measures your real placements and breaks while you play (placed → shown, hit → gone, hit → confirmed by the server, next crystal on the same block, FPS) under the crystal optimizer in charge, Marlow's included. It only observes. A **Normal**/**Dev** switch picks plain explanations or the full statistics.
- **Crystal Practice** (experimental), a practice world of its own:
  - **A bot that plays real crystal PvP techniques** by difficulty: hit-crystal, respawn anchors, face-placing and pearls on Normal; d-taps timed to the end of your damage immunity, safe anchors behind glowstone, top-blocking, hiding in holes and mending on Hard; the butterfly, double anchors, triple taps, chain pops, crits and fall prediction on Extreme. None of them can be switched off: the menu lists them, with how each one works. The bot carries your own kit and is held to human timing.
  - **Your kit:** netherite, diamond or iron armour (Protection IV, Unbreaking III and Mending, Blast Protection IV on up to two pieces), a sword with **Knockback I or II**, **1 to 20 totems** or a full inventory, and presets modelled on **MCTiers**, **MCPVP** and **PVPHQ**. None of those ladders publishes its crystal kit item by item, so the presets say what they are based on.
  - **Two styles:** **Smart** blocks off with a block in front of itself when you threaten it, hides in holes and pearls away to heal instead of hopping backwards; **Aggressive** rushes, never backs off and pearls in from further out. Both mine their way out with the pickaxe if you box them in.
  - **Aimed pearls:** before throwing, the bot simulates the pearl's flight tick by tick against the terrain and throws only when it lands on safe ground where it wants to be.
  - **Arrange inventory:** a kit editor with a kit room, click or drag to move, right click to empty, **Save** and **Reset**. The kit is handed out exactly as you arranged it.
  - **Three grounds:** a flat of netherite blocks, a hole arena, or an almost flat meadow with few trees and no caves in one of eight biomes (plains, desert, taiga, snowy plains, savanna, cherry grove, badlands, the End). Every death starts a new round on a rebuilt arena.
- **Herzium integration:** dimmed without [Herzium](https://modrinth.com/mod/herzium); with it, sets Herzium's hotbar order from here.

## Fixed

- **Ghost crystals crashed the game on 26.2 and 26.3** ("Tried to access entity ID before ID assignment"). Fixed: the stand-in crystal now has an id of its own, and nothing looks crystals up by id any more.

- Your own crystals were drawn with the enemy profile while spamming. Placements are now queued per base and claimed in the order you sent them.
- The settings preview always exploded with the plain burst instead of your flash style.
- The death flash's light on the ground tore apart as it faded, and the same explosion lit the ground only sometimes during a fight.
- **On 26.2 and 26.3 the glow cut dark discs into the light around it**: those versions made the material the glow is drawn with hide everything behind it, including the light on the ground and other crystals' glow. The glow now uses its own copy of that material that lets lights add up again.
- At high power the glow looked distorted: the crystal's turning frames cut it into flickering pieces, and the ground cut it off in a straight line. The halo now sits just behind the crystal and fades out before the ground, and strong reflections fade out at their edge instead of ending in a hard square.
- The Conflict Monitor drew a found mod's icon as a solid strip over the report's text. The icon now sits in its card.
- Turning on the glow's own colour repainted the crystal's layers. It now belongs to the light alone.
- A crash while saving could reset every setting on the next launch.

## Fair play

- **Lightning** only points at players you could see when the crystal exploded: not invisible, not spectating, not behind a wall. Every bolt has the same length, so none of them reveals where anyone is.
- Nothing in this release adds, delays or changes a packet. The only difference from Vanilla on the wire is still Safe Crystal skipping the obsidian you would have mined by mistake, and you can now switch it off.

## Versions

- **Minecraft 26.3** is supported. It swapped its windowing library and dropped the native file chooser, so on 26.3 a custom explosion sound is chosen by dropping the `.wav`, `.ogg` or `.mp3` into the mod's sounds folder and pressing the button again. Mod Menu for 26.3 is still a beta at the time of this release.

Available for Fabric on **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**. Each download supports only the Minecraft version named on its file.

Minecraft **1.21.10 and older** no longer receive updates. Their last release, 2.2.7, stays available here.

Install the matching **Fabric API**. **Mod Menu** is optional.
