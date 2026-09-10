# Crystal Tweaks 2.2.11 - The optimizer stays on unless a real conflict is found

**If the optimizer stopped working in 2.2.10, this is the fix.**

- Fixed the native optimizer staying off for the whole session when the compatibility check could not read every mod in the pack. An unreadable JAR is not a conflict, so break prediction, ghost crystals, Safe Crystal and placement tracking now keep running, and the settings screen says the check was incomplete.
- Fixed mods that only share `Connection.send` being mistaken for crystal optimizers. Protocol translators, network performance mods and ping displays no longer disable anything. The overlapping mod now has to be about crystals before Crystal Tweaks yields to it.
- Fixed a failed metadata read ending detection early instead of falling through to the Mixin scan.
- Added **Re-check compatibility** to Advanced Tweaks: re-runs detection without restarting the game, for when you have just removed the conflicting mod.
- The retired **KoHs Crystal Tweaks** is now detected by name, so the notice tells you which JAR to remove.

Unchanged: with Marlow's Crystal Optimizer, No Crystal Break or any other detected crystal optimizer installed, all Crystal Tweaks interaction helpers stay off on purpose so the two do not fight over the same crystal. Every visual feature keeps working: colors, Glow editor, afterglow, other-player crystal profiles, sounds and the interactive preview.

Available for Fabric on **1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2**. Each download supports only the Minecraft version named on its file.

Install the matching **Fabric API**. **Mod Menu** is optional.
