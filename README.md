<p align="center">
  <img src="src/main/resources/assets/crystal_tweaks/icon.png" alt="Crystal Tweaks icon" width="180">
</p>

<h1 align="center">Crystal Tweaks</h1>

<p align="center">
  A client-side Fabric mod for customizable End Crystal visuals, sounds, and responsive previews.
</p>

<p align="center">
  <a href="https://modrinth.com/mod/kohs-crystal-tweaks"><img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-Download-00AF5C?logo=modrinth&amp;logoColor=white"></a>
  <a href="docs/WIKI.md"><img alt="Wiki" src="https://img.shields.io/badge/Documentation-Wiki-7C3AED?logo=gitbook&amp;logoColor=white"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&amp;logoColor=white"></a>
  <a href="https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new"><img alt="Report an issue" src="https://img.shields.io/badge/GitHub-Report_an_Issue-D73A49?logo=github&amp;logoColor=white"></a>
</p>

## About

Crystal Tweaks brings End Crystal visual and sound controls into one compact configuration screen. The 2.2.9 update adds a full Glow editor, a separate appearance profile for other players' crystals, and automatic coexistence with other crystal optimizers.

## Highlights

- Independent colors for the outer layer, inner layer, and crystal core.
- Glow editor with `0%`–`300%` power, colored reflections, custom glow color, and a smooth afterglow trail.
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

Crystal Tweaks provides separate JARs for:

```text
1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
26.1, 26.1.1, 26.1.2, and 26.2
```

Each JAR declares one exact Minecraft version. Always download the file matching the instance where it will be installed.

## Compatibility and multiplayer

Do not load Crystal Tweaks together with the retired KoHs Crystal Tweaks mod or another mod that owns the same crystal input, prediction, or interaction paths. Competing mixins can produce inconsistent behavior even when one mod declares a higher mixin priority.

Server rules differ. Review the rules of every multiplayer server and obtain staff approval when required; this project cannot guarantee acceptance by every server or anticheat.

Crystal Tweaks never fabricates, delays, reorders or duplicates a gameplay packet, and never changes
reach, rotation, attack cooldown or click rate. The server stays authoritative over every crystal.

Its one predictive behavior is hiding a crystal you just hit before the server confirms the break,
which is latency compensation for an action you already performed. It is constrained to hits the
server is expected to accept.

See [docs/LEGITIMACY_AUDIT.md](docs/LEGITIMACY_AUDIT.md) for the full client/server boundary of every feature,
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

## License

Crystal Tweaks is maintained by **zymekoh** and distributed under the [MIT License](LICENSE).
