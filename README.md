# Crystal Tweaks

[![GitHub](https://img.shields.io/badge/GitHub-Crystal--Tweaks-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/kohs-crystal-tweaks)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/crystal_tweaks/icon.png" alt="Crystal Tweaks icon" width="220">
</p>

**Customizable End Crystal visuals, sounds, and responsive previews.**

## About

Crystal Tweaks brings End Crystal visual and sound controls into one compact configuration screen. Version 2.3.0 adds flash styles for a destroyed crystal and gathers every glow control into one editor, fixes your own crystals being drawn with the enemy profile while spamming, and keeps the native optimizer standing down only for a mod actually found to be optimizing crystals.

## What it changes

- Independent colors for the outer layer, inner layer, and crystal core.
- Glow editor with `0%`–`300%` power, colored reflections, custom glow color, and a smooth afterglow trail.
- Flash styles for a destroyed crystal, in the same glow color: explosion, skull, Steve head, or bolts thrown toward nearby players.
- Glow that respects walls instead of using an outline drawn through terrain.
- Separate colors, glow, and animation speeds for crystals that were not placed by you (with approximate identification).
- Automatic optimizer conflict handling: interaction helpers step aside while visuals, sounds, and the preview stay active.
- Rotation and floating speed controls from `0%` to `300%`.
- Custom local explosion sounds with volume and playback-speed controls.
- Interactive preview using the player's mapped Attack and Use Item controls.
- Responsive translucent interface with animated purple particles.
- Optional ghost crystals (off by default), scroll hints, and a local conflict monitor.
- Local Conflict Monitor for qualified, exact Mixin class-and-method overlaps.
- Spanish localization for Minecraft language codes beginning with `es`; English for every other locale.
- Optional Mod Menu integration.

See the [Crystal Tweaks Wiki](docs/WIKI.md) for installation, configuration, preview controls, compatibility notes, and troubleshooting.

## Requirements

- Fabric Loader: each JAR declares the exact minimum it was built against; see
  [`gradle/versions.properties`](gradle/versions.properties).
- The Fabric API version matching your Minecraft installation.
- Java 21 for Minecraft 1.21–1.21.11.
- Java 25 for Minecraft 26.1–26.2.
- Mod Menu is optional but recommended for opening the configuration screen.

## Supported Minecraft versions

The source build matrix contains these exact Minecraft targets:

```text
1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
26.1, 26.1.1, 26.1.2, and 26.2
```

The 2.3.0 release notes cover **1.21.11, 26.1, 26.1.1, 26.1.2, and 26.2**. Earlier targets remain in the source matrix; use the release available for your exact game version. Each JAR declares one exact Minecraft version.

## Compatibility and multiplayer

Do not load Crystal Tweaks together with the retired KoHs Crystal Tweaks mod or another mod that owns the same crystal input, prediction, or interaction paths. Competing mixins can produce inconsistent behavior even when one mod declares a higher mixin priority.

Server rules differ. Review the rules of every multiplayer server and obtain staff approval when required; this project cannot guarantee acceptance by every server or anticheat.

Crystal Tweaks never fabricates, delays, reorders or duplicates a gameplay packet, and never changes
reach, rotation, attack cooldown or click rate. The server stays authoritative over every crystal.

Its one predictive behavior is hiding a crystal you just hit before the server confirms the break,
which is latency compensation for an action you already performed. It is constrained to hits the
server is expected to accept.

See [docs/audits/LEGITIMACY_AUDIT.md](docs/audits/LEGITIMACY_AUDIT.md) for the full client/server boundary of every feature,
including the two places where the mod does change what a later packet contains.

## Building

Every supported Minecraft version has its own row in [`gradle/versions.properties`](gradle/versions.properties),
holding the Fabric Loader, Fabric API, Mod Menu and Java release that target is built against. Pick a
target with `-Pmc`:

```powershell
.\gradlew.bat build -Pmc=26.1.2
```

To build the whole matrix, collect the JARs into `versions/` and refresh `CHECKSUMS.sha256`:

```powershell
.\tools\build-all.ps1
```

See [docs/RELEASING.md](docs/RELEASING.md) for the full release process and for how to add a new Minecraft
version.

## Support

- [Read the wiki](docs/WIKI.md)
- [Report a problem](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)
- [Join the Discord server](https://discord.gg/9t2VxEF7UU)
- [Download from Modrinth](https://modrinth.com/mod/kohs-crystal-tweaks)

## Repository layout

| Location | Contents |
| --- | --- |
| `src/` | Shared client sources and the version-specific rendering adapters. |
| `gradle/` | Wrapper and the exact Minecraft dependency matrix. |
| `docs/` | Wiki, release workflow, audits and version notes. |
| `tools/` | Build, artifact, Mixin and appearance checks. |
| `versions/`, `dist/` | Local generated JAR collections; excluded from Git. |

Start with the [documentation index](docs/README.md). Build output and local
Minecraft instances remain outside Git; keep installable JARs out of the source tree.

## License

Crystal Tweaks is maintained by **zymekoh** and distributed under the [MIT License](LICENSE).
