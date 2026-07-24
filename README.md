# KoHs Crystal Tweaks

KoHs Crystal Tweaks is a client-only Fabric mod with configurable Crystal PvP visuals, sound, and safety-focused interaction corrections for Minecraft 1.21 through 26.2.

[Modrinth project page](https://modrinth.com/mod/kohs-crystal-tweaks) · [GitHub releases](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/releases)

## 2.0.4 safety release

Version 2.0.4 removes behavior that could change the timing, identity, or ordering of normal multiplayer interactions:

- Custom client-identification and compatibility payloads have been removed.
- Sub-tick action queues, delayed actions, action replay, and automatic retries have been removed.
- The mod never changes the selected hotbar slot automatically.
- One physical attack or use input can produce at most one corresponding vanilla action.
- The visual crystal preview is non-targetable, does not enter the client entity registry, and is OFF by default.
- Optional local cleanup is limited to the exact real, server-provided crystal selected by the player's normal vanilla attack. It is OFF by default.

The server remains authoritative for placement, entity IDs, damage, and explosions.

> No client mod can provide a universal anti-cheat guarantee. Server rules differ, and some servers prohibit all gameplay-related client modifications. Check the rules of the server before enabling optional features.

## Available builds

| Minecraft | Java | Version | Artifact |
|---|---:|---|---|
| 1.21–1.21.1 | 21 | `2.0.4+mc1.21` | `kohs-crystal-tweaks-2.0.4+mc1.21.jar` |
| 1.21.2–1.21.4 | 21 | `2.0.4+mc1.21.2` | `kohs-crystal-tweaks-2.0.4+mc1.21.2.jar` |
| 1.21.5 | 21 | `2.0.4+mc1.21.5` | `kohs-crystal-tweaks-2.0.4+mc1.21.5.jar` |
| 1.21.6–1.21.8 | 21 | `2.0.4+mc1.21.6` | `kohs-crystal-tweaks-2.0.4+mc1.21.6.jar` |
| 1.21.9 | 21 | `2.0.4+mc1.21.9` | `kohs-crystal-tweaks-2.0.4+mc1.21.9.jar` |
| 1.21.10 | 21 | `2.0.4+mc1.21.10` | `kohs-crystal-tweaks-2.0.4+mc1.21.10.jar` |
| 1.21.11 | 21 | `2.0.4+mc1.21.11` | `kohs-crystal-tweaks-2.0.4+mc1.21.11.jar` |
| 26.1 | 25 | `2.0.4+mc26.1` | `kohs-crystal-tweaks-2.0.4+mc26.1.jar` |
| 26.1.1 | 25 | `2.0.4+mc26.1.1` | `kohs-crystal-tweaks-2.0.4+mc26.1.1.jar` |
| 26.1.2 | 25 | `2.0.4+mc26.1.2` | `kohs-crystal-tweaks-2.0.4+mc26.1.2.jar` |
| 26.2 | 25 | `2.0.4+mc26.2` | `kohs-crystal-tweaks-2.0.4+mc26.2.jar` |

Each JAR is restricted to the Minecraft range shown by its filename and Fabric metadata.

## Features

- Crystal frame and core tint controls.
- Crystal animation, flotation, and static-render options.
- Custom End Crystal explosion sound support on compatible branches.
- Safe Crystal, which can protect normal obsidian from accidental mining while an End Crystal is held.
- Optional render-only visual placement preview.
- Optional exact-entity local cleanup after a normal attack on a real server-provided crystal.
- Responsive configuration screens with hover descriptions.

Visual and sound settings do not create combat actions. Gameplay-related optional features should still be evaluated against the rules of the server being used.

## Safety defaults

The two features with the greatest potential to affect combat perception are disabled on a fresh installation:

- Visual crystal preview: OFF.
- Confirmed local crystal cleanup: OFF.

Saved user choices are preserved when possible. Disabling a feature restores the corresponding vanilla path without producing a replacement action.

## Network and input boundary

KoHs 2.0.4 does not register custom identification channels and does not send a mod-identification message when joining a server. It does not generate combat packets directly.

For an actual player input, Minecraft's normal interaction code remains responsible for the resulting action:

```text
one physical input -> zero or one vanilla action -> server validation
```

The mod does not turn held input into repeated actions, replay consumed input, delay an action until a target appears, remove vanilla cooldowns, guess entity IDs, or change slots automatically.

## Building

Each folder under `version/` is an independent Gradle project.

Java 21 example:

```powershell
cd "version\1.21.11"
.\gradlew.bat clean build --no-daemon
```

Minecraft 26.x requires Java 25:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
cd "version\26.2"
.\gradlew.bat clean build --no-daemon
```

Remapped JARs are written to each project's `build/libs/` directory. Release preparation does not launch Minecraft.

## Documentation

- [Safety investigation and decisions](docs/INVESTIGATION.md)
- [Changelog](CHANGELOG.md)
- [Per-version release notes](release-notes)

## License

KoHs Crystal Tweaks is licensed under the [MIT License](LICENSE).
