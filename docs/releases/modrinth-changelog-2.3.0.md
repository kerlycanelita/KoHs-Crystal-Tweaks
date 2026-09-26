# Crystal Tweaks 2.3.0 - Fourteen flashes, a brand-new settings screen

## New

- **Flash styles.** The flash a crystal leaves when it explodes can now take a shape, in your own glow colour: the original **explosion**, a **skull**, **your own head** as a pale ghost, **lightning**, and ten new ones: **heart**, **star**, **shockwave**, **vortex**, **crown**, **crescent**, **snowflake**, **flower**, **gem** and **crossed swords**. The shockwave, vortex, star and flower move as the flash fades.
- **Flash size** from `10%` to `300%` for every shaped style.
- **A new settings screen.** Four tabs (**Colors**, **Glow**, **Sound**, **Advanced**), controls grouped on cards, switches with sliding knobs, a status chip that tells you what the optimizer is doing, animated tabs and rows, and an obsidian pedestal under the preview crystal. Click the preview to see your flash; changing the flash style shows it straight away.
- **Safe Crystal can be switched off** in Advanced, for servers that do not allow input filters. It stays on by default.
- An **important notice** the first time you open the settings in a session, only when no crystal optimizer is installed: Crystal Tweaks optimizes your client only; for placements that reach the server sooner, Marlow's Crystal Optimizer is recommended. You can turn the notice off for good.

## Fixed

- Your own crystals were drawn with the enemy profile while spamming. Placements are now queued per base and claimed in the order you sent them.
- The settings preview always exploded with the plain burst instead of your flash style.
- The death flash's light on the ground tore apart as it faded, and the same explosion lit the ground only sometimes during a fight.
- At high power the glow looked distorted: the crystal's turning frames cut it into flickering pieces, and the ground cut it off in a straight line. The halo now sits just behind the crystal and fades out before the ground, and strong reflections fade out at their edge instead of ending in a hard square.
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
