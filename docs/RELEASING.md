# Releasing Crystal Tweaks

Crystal Tweaks ships one JAR per Minecraft version. Each JAR is compiled against that version's own
mapped API, so every target is a separate Gradle invocation.

## The build matrix

[`gradle/versions.properties`](gradle/versions.properties) is the single source of truth:

```properties
<minecraft> = <fabric_loader>, <fabric_api>, <mod_menu>, <java_release>
```

`build.gradle` reads it, so selecting a target is one flag:

```powershell
.\gradlew.bat build -Pmc=26.1.2
```

Any individual value can still be overridden on the command line, and an explicit `-P` always wins
over the matrix:

```powershell
.\gradlew.bat build -Pmc=26.1.2 -Pfabric_version=0.155.2+26.1.2
```

The Fabric Loader column is not decoration. It becomes the `depends.fabricloader` floor written into
the published `fabric.mod.json`, so it must stay at the loader the JAR is genuinely built against.
Raising it without rebuilding locks players out of the mod on that Minecraft version.

## Cutting a release

1. Bump `mod_version` in `gradle.properties`.
2. Add the release section to `CHANGELOG.md`.
3. Update `LEGITIMACY_AUDIT.md` if any client/server behavior changed.
4. Build everything:

   ```powershell
   .\tools\build-all.ps1
   ```

   This clears `build\libs`, builds each matrix target in order, copies the JARs into `versions\`
   and rewrites `CHECKSUMS.sha256` from the files that are actually present.

5. Verify each JAR declares what it was built against:

   ```powershell
   .\tools\verify-jars.ps1
   ```

6. Verify every Mixin target actually exists in that version's Minecraft classes:

   ```bash
   python tools/verify-mixins.py
   ```

   This is the check nothing else performs. Mixin resolves `@Mixin`, `@Inject`, `@Invoker` and
   `@Accessor` targets at runtime, not at compile time, so a JAR aimed at a member that does not
   exist on that Minecraft version compiles cleanly and then aborts the game on launch. The script
   reads each JAR in its own mapping namespace — `intermediary` for the 1.x targets, `official`
   for 26.x — and checks every target against the cached Minecraft classes for that exact version.

7. Run at least one dev client per mapping family to confirm the Mixins apply at runtime:

   ```bash
   ./gradlew runClient -Pmc=26.1.2
   ./gradlew runClient -Pmc=1.21.11
   ```

   Reaching the main menu with no Mixin error in the log covers both branches: 26.x goes through
   `MinecraftPickInvoker`, and the 1.x targets use the public `GameRenderer.pick` instead. Realms
   authorisation warnings are expected in a dev client and are unrelated.

8. Move the previous release into `versions\archive\<old-version>\`.
9. Upload the JARs from `versions\` and publish the checksums.

`versions/` and `dist/` are ignored by git; the published hashes in `CHECKSUMS.sha256` are what the
repository records about a release.

## Adding a new Minecraft version

1. Add a row to `gradle/versions.properties` with the loader, Fabric API, Mod Menu and Java release
   for that version.
2. Try a build: `.\gradlew.bat build -Pmc=<new version>`.
3. If it fails on renamed vanilla APIs, extend the source transforms near the top of `build.gradle`.
   Those are keyed off `usesRemapping`, `usesPreIdentifierNames`, `usesLegacyMouseApi`,
   `usesLegacyEntityPreview`, `usesLegacyStateDispatcher`, `usesOldCrystalRenderer`,
   `usesOldIconBlit`, `usesRenderTypeIconBlit`, `usesLegacyTextureConstructor` and
   `usesModernRegistrySplit`.
4. Every one of those flags is declared as an input of `generateLegacyClientSources`. A new flag
   must be declared there too, otherwise Gradle will consider the task up to date across a version
   switch and build the new target from the previous target's rewritten sources.
5. Widen the `minecraft` range in `src/main/resources/fabric.mod.json` and the supported-version list
   in `README.md`.

## Source layout

| Path | Applies to |
| --- | --- |
| `src/main` | Version-independent entrypoint and mod metadata. |
| `src/client` | The real implementation. Written against the newest API and rewritten per target. |
| `src/legacy` | Replacements used only by the `1.x` targets. |
| `src/legacyOld` | Additional replacements for 1.21 and 1.21.1, which still use the old crystal renderer. |

`src/client` is copied into `build/generated/sources/crystalTweaksLegacy` and rewritten there. Never
edit the generated copy; it is replaced on every build.

## Runtime checks that the build cannot do

The build compiles every target but never launches the game. Before publishing, verify by hand on at
least one modern and one legacy target:

- placing and breaking crystals at high ping, both tap and hold;
- main hand and off hand;
- the obsidian attack guard while holding a crystal;
- `Instant break` on and off;
- remapped Attack and Use keys;
- the configuration screen at GUI scale 1 and at the maximum scale.
