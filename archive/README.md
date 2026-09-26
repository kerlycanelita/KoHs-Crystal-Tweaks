# Archived targets

[Back to overview](../README.md)

Since 26 September 2026 Crystal Tweaks supports **Minecraft 1.21.11 and later**:
1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Minecraft 1.21 through 1.21.10 are
no longer built, tested or fixed. Nothing was deleted: the code only those
targets used was moved here, and the rest stays in the history.

| Path | Minecraft | Contents |
| --- | --- | --- |
| `src/legacyOld/` | 1.21–1.21.1 | Adapters for the crystal renderer that predates render states: `LegacyCrystalRendererMixin` (layer colours and animation speeds) and `LegacyEndCrystalRendererAccessor` (settings-screen preview). Moved unchanged. |
| `gradle/versions.properties` | 1.21–1.21.10 | The matrix rows those targets were built against: Fabric Loader, Fabric API, Mod Menu and Java. |
| `gradle/build-2.3.0.gradle` | 1.21–1.21.11 | Unchanged copy of `build.gradle` as of 2.3.0. It holds every source rewrite the archived targets needed: names from before `Identifier`, the old mouse and key API, the two legacy entity previews, the old icon blit and the old texture constructor. Reference only; no build reads it. |

The last release published for every archived target is **2.2.7** (Modrinth,
26 August 2026). A 2.3.0 JAR was built for 1.21.10 but never published; its
SHA-256 was
`b4edcbb7a1d5a90cff892f6061d034b8740301f09e7761c4652d46f1fdea001d`.

## Rebuilding an archived target

`build.gradle` refuses the archived versions. Build them from 2.3.0
(`476e9ed`), the last commit that still carries their rewrites:

```powershell
git checkout 476e9ed
.\gradlew.bat build "-Pmc=1.21.10"
```

Keep the quotes around `-Pmc` in Windows PowerShell 5.1, which otherwise splits
the argument at the dot.

## Bringing a target back

1. Restore its row from `gradle/versions.properties` into the root matrix.
2. Port the rewrites it needs from `gradle/build-2.3.0.gradle` into
   `build.gradle`, declaring every new flag as an input of
   `generateLegacyClientSources`.
3. For 1.21 or 1.21.1, move `src/legacyOld` back with `git mv` and list its two
   Mixins again in the `processClientResources` filter.
4. Relax the archived-version check near the top of `build.gradle`, then run
   `tools/verify-jars.ps1` and `tools/verify-mixins.py` on the new JAR.

Local builds of archived targets go in `archive/builds/`, which Git ignores.
