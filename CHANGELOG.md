# Changelog

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
