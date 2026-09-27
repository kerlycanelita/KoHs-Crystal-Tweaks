[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)
[![GitHub](https://img.shields.io/badge/GitHub-Code-181717?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks)
[![Report an issue](https://img.shields.io/badge/GitHub-Report%20an%20issue-D73A49?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)
[![Wiki](https://img.shields.io/badge/Wiki-Guide-7B2CBF?logo=readthedocs&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/wiki)

# Crystal Tweaks 2.3.0

![Crystal Tweaks glow showcase](https://cdn.modrinth.com/data/dwVY5imH/images/420fb8851c3ce3cabf6ceb668daa4904157cac69_350.webp)

## Glow

Open the **Glow** tab in the settings:

- **Power:** from 0 to 300%. An extra glow layer keeps the effect visible in any lighting, not only at night.
- **Reflections:** simulates colored light on the top faces of nearby blocks.
- **Custom color:** uses the same color picker as the rest of the menu. It colors the light only; the crystal's own layer colors stay as you set them.
- **Flash on explosion:** the light a destroyed crystal leaves behind fades smoothly, in one of fourteen styles, sized from 10% to 300%.

The glow respects walls and does not use Vanilla's outline that shows through terrain.

![Flash styles](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/flash-styles.png)

![Glow settings](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/settings-glow.png)

## Other players' crystals

Give crystals you did not place their own visual profile, with separate colors, glow and animation speeds so they stand out immediately. Identification is approximate because the server does not tell the client who placed a crystal. Anything that does not match your recent placements—including crystals that were already there when you joined—uses the other-player profile.

## Works alongside other mods

**With Marlow's or another detected crystal optimizer installed, all Crystal Tweaks interaction helpers stay off:** break prediction, ghost crystals, Safe Crystal and placement tracking. Pending predictions are cleared, and saved settings cannot reactivate these helpers during a conflict.

The helpers also stay off while compatibility is being checked. If the check cannot read every mod, they stay on and the menu says so. Colors, glow, other-player crystal appearances, sounds and the preview stay available. The menu shows why the helpers are paused.

**Client Side Crystals, HCsCR and the "... Crystal Optimizer" mods count as optimizers:** Client Side Crystals draws its own stand-in crystal the moment you place one, which is the ghost crystals' job. Mods that only change how a crystal looks or count crystals never pause anything, and neither do performance mods such as Krypton, Sodium or Lithium.

You also get optional ghost crystals (off by default), a scroll hint when more options are available, and a conflict monitor that warns about possible mod clashes.

## Built for fair play

Crystal Tweaks only changes local presentation and local helpers. It does not change reach, camera, cooldowns or click timing, and it does not invent or repeat actions. Server rules can differ, so always check the rules of the server you play on.

## Compatibility

Minecraft **1.21.11**, **26.1**, **26.1.1**, **26.1.2**, **26.2** and **26.3** on Fabric. Download the file matching your exact Minecraft version and install the matching Fabric API. Mod Menu is optional.

**Archived versions:** Minecraft **1.21 to 1.21.10** are archived. Their last release, **2.2.7**, stays available for download here, but they no longer receive updates or fixes.
