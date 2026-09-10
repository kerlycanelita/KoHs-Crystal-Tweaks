# Crystal Tweaks 2.2.10 - Optimizer compatibility fixes

- Improved detection of Marlow's Crystal Optimizer and No Crystal Break.
- When another optimizer is detected, Crystal Tweaks stops its own crystal interaction helpers and clears pending predictions.
- All interaction helpers stay off until the compatibility check finishes successfully. If the check cannot finish, they remain off.
- With another optimizer present, break prediction, ghost crystals, Safe Crystal and placement tracking stay disabled, even if they were previously enabled in your settings.
- Fixed a case where a queued crystal-break effect could run after the compatibility check had disabled it.
- Stopped collecting placement timing samples while another optimizer is in control.
- Restored normal Mixin priority to reduce unnecessary interference with other mods.
- Kept the Glow editor, 0-300% controls, smooth afterglow, other-player crystal profiles, custom colors, sounds and interactive preview.
- The menu now clearly shows when ghost crystals and interaction helpers are paused.

Available for Fabric on **1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2**. Each download supports only the Minecraft version named on its file.

Install the matching **Fabric API**. **Mod Menu** is optional.
