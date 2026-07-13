# Changelog — 1.21.2–1.21.4

## 2026-04-24

- Split the old 1.21.2–1.21.9 group so this source set covers only 1.21.2–1.21.4.
- Corrected an overly broad compatibility range that extended beyond the real internal API and could fail at runtime on later patch versions.
- Kept this directory on the 1.21.2 build target, which matches the renderer and client APIs through 1.21.4.
- Updated `fabric.mod.json` to declare `>=1.21.2 <1.21.5`.
- Updated the README and metadata to document the real range.
- Confirmed a clean `gradlew.bat clean build`.
