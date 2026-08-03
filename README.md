# Crystal Tweaks

Client-side Fabric mod that improves End Crystal interactions, protects obsidian placement, and adds visual and sound customization.

## Core

- Locally removes an attacked End Crystal while the server processes the real attack packet.
- Recalculates the local target after an attack so the removed crystal does not block the next click.
- Prevents accidental obsidian mining while attempting to place an End Crystal with either hand.
- Does not modify mouse movement or the camera.

Both functions are independent internally and remain permanently enabled as part of the core.

## Visuals

- Independent colors for the outer layer, inner layer, and crystal core.
- Compact HSV picker with hexadecimal input.
- Rotation and floating speed from 0% to 300%; 100% is vanilla and 0% makes that motion static.
- Animated preview that uses Minecraft's active texture or the current resource pack.
- Interactive preview without a visible base: mapped Attack explodes it and mapped Use Item places it again.
- Responsive translucent panel with animated purple particles and matched pixel-rounded corners.
- The Advanced Tweaks tab reserves space for future options with an animated purple snake.

## Sounds

- Local End Crystal explosion sound replacement.
- WAV, OGG, and MP3 files up to five seconds long.
- Volume from 0% to 200% and playback speed from 0.5x to 2.0x.
- The interactive preview plays the configured custom sound with its volume and speed settings. It falls back to the vanilla explosion sound when no custom sound is active.

All controls are shown in Spanish for every Minecraft language code beginning with `es`; every other language uses English. Visuals, Sounds, and Advanced Tweaks remain responsive at high GUI scales, with scrollable controls and an optional preview pane.

## Supported versions

- Minecraft 1.21–1.21.11: Java 21.
- Minecraft 26.1–26.2: Java 25.
- Fabric Loader 0.19.3 or newer.
- The Fabric API release matching the selected Minecraft version.
- Mod Menu is optional and provides access to the configuration screen.

Each JAR declares exactly one Minecraft version to prevent accidental loading in an incompatible instance.

## Building

The default configuration targets Minecraft 26.2:

```powershell
.\gradlew.bat build
```

The 16 builds for Crystal Tweaks 2.2.4 are collected in `versions`, with an additional copy in `dist/2.2.4`.
