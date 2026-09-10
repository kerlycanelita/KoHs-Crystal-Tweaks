[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)
[![GitHub](https://img.shields.io/badge/GitHub-Code-181717?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks)
[![Report an issue](https://img.shields.io/badge/GitHub-Report%20an%20issue-D73A49?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)
[![Wiki](https://img.shields.io/badge/Wiki-Guide-7B2CBF?logo=readthedocs&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/wiki)

# Crystal Tweaks 2.2.9

The biggest update yet: more control over your crystal glow and an easy way to tell your crystals apart from other players' crystals.

![Crystal Tweaks glow showcase](https://cdn.modrinth.com/data/dwVY5imH/images/420fb8851c3ce3cabf6ceb668daa4904157cac69_350.webp)

## Glow editor

Open the editor under **Glow** in **Visuals**:

- **Power:** from 0 to 300%. An extra glow layer keeps the effect visible in any lighting, not only at night.
- **Reflections:** simulates colored light on the top faces of nearby blocks.
- **Custom color:** uses the same color picker as the rest of the menu. It can replace the layer colors while enabled; turning it off restores them.
- **Trail:** the glow fades smoothly after an explosion instead of disappearing instantly.

The glow respects walls and does not use Vanilla's outline that shows through terrain.

## Other players' crystals

Give crystals you did not place their own visual profile, with separate colors, glow and animation speeds so they stand out immediately. Identification is approximate because the server does not tell the client who placed a crystal. Anything that does not match your recent placements—including crystals that were already there when you joined—uses the other-player profile.

## Works alongside other mods

When another crystal optimizer—Marlow's or any other—is detected, Crystal Tweaks steps aside so two optimizers do not interfere with each other. Only interaction helpers are disabled (break prediction, ghost crystals and Safe Crystal); all visual features continue to work, including colors, glow, other-player crystals, sounds and the preview. The notice tells you which mod caused the conflict.

**Client Side Crystals is not treated as a conflict:** it only draws crystals, so both mods can run together.

You also get optional ghost crystals (off by default), a scroll hint when more options are available, and a conflict monitor that warns about possible mod clashes.

## Built for fair play

Crystal Tweaks only changes local presentation and local helpers. It does not change reach, camera, cooldowns or click timing, and it does not invent or repeat actions. Server rules can differ, so always check the rules of the server you play on.

## Compatibility

Minecraft **1.21.11**, **26.1**, **26.1.1**, **26.1.2** and **26.2** on Fabric. Download the file matching your exact Minecraft version and install the matching Fabric API. Mod Menu is optional.
