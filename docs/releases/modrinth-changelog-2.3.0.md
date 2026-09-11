# Crystal Tweaks 2.3.0 - Flash styles, and your crystals stay yours

## New

- **Flash styles.** The flash a crystal leaves when it explodes can now take a shape: the original **explosion**, a **skull**, a **Steve head**, or **lightning** thrown toward the players around the blast. All four use your own glow colour and need glow power above 0%.
- Every glow control now lives in one place. The **Glow** editor holds power, reflections, the flash style, the custom-colour override, the hex box and the picker together.

## Fixed

- Your own crystals were drawn with the enemy profile while spamming. Each base remembered a single placement, so placing, breaking and placing again on the same obsidian produced two crystals against one record: the first took it and the second was read as someone else's. Placements are now queued per base and claimed in the order you sent them.
- The death flash's light on the ground tore apart as it faded. Each block the explosion destroyed was permanently removing its own patch of light from the flash, and a chunk that reloaded mid-fade never got it back.
- The same explosion lit the ground only sometimes during a fight. When the terrain sampling budget ran out, a crystal reported no lit surfaces at all and that empty answer was captured into the flash.

## Compatibility

This release also carries the 2.2.11 fixes, which were never published:

- The native optimizer no longer stays off when the compatibility check cannot read every mod in your pack. Only a mod actually found to be optimizing crystals turns the helpers off.
- Performance mods can never stand this mod down. Krypton, Lithium, Sodium, C2ME, ImmediatelyFast, ScalableLux, FerriteCore, MoreCulling, EntityCulling, ModernFix, ViaFabricPlus, ViaVersion and others are named in the detector, because several of them genuinely share the same network and rendering targets and that overlap says nothing about crystals.
- **Re-check compatibility** in Advanced Tweaks re-runs the detection without restarting the game.

With Marlow's Crystal Optimizer, No Crystal Break or any other detected crystal optimizer installed, **every** interaction helper stays off on purpose: break prediction, ghost crystals, placement tracking and **Safe Crystal**. That mod owns the crystal click, and two mods deciding whether the same swing mines a block is how you end up unable to break obsidian at all. Every visual feature keeps working, flash styles included.

Available for Fabric on **1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2**. Each download supports only the Minecraft version named on its file.

Install the matching **Fabric API**. **Mod Menu** is optional.
