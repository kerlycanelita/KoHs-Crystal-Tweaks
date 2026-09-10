# Crystal-Tweaks documentation

[Back to overview](../README.md)

## Guides

- [Wiki](WIKI.md)
- [Release workflow](RELEASING.md)
- [2.2.10 audit](audits/review-2.2.10.md)
- [Client/server boundary](audits/LEGITIMACY_AUDIT.md)
- [2.2.10 release notes](releases/modrinth-changelog-2.2.10.md)

## Root build

| Setting | Value |
| --- | --- |
| Minecraft | `26.2` |
| Mod version | `2.2.10` |
| Loader | Fabric `0.19.3` |
| Loom | `1.17-SNAPSHOT` |
| JDK | 25 |

Official Minecraft names for the 26.x root target. Older 1.21.x targets use the remapping branch in `build.gradle`.
The source of truth is [gradle.properties](../gradle.properties) and
[build.gradle](../build.gradle); each additional target declares its own dependencies.

## Working folders

Run build commands from the repository root unless a target's instructions say
otherwise. `build/`, `.gradle/` and `run/` hold local build or game state and are
excluded from Git. Preserve saves, configuration and logs when organizing files.
Audits describe the version and checks recorded at the time; they do not imply
that every later change has been tested in a running game.
