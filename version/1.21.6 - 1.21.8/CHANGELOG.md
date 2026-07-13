# Changelog — 1.21.6–1.21.8

## 2026-04-24

- Split 1.21.6–1.21.8 from the previous range because 1.21.6 no longer shares real binary compatibility with the 1.21.5 build target.
- Fixed a 1.21.6 `NoSuchMethodError` in `KoHsCrystalTweaksConfigScreen` that could crash the old 1.21.5–1.21.8 build when opening the mod menu.
- Recompiled this source set against `minecraft_version=1.21.6` and narrowed its metadata to `>=1.21.6 <1.21.9`, aligning the bytecode with the declared range.
- Confirmed a clean `gradlew.bat clean build` in this directory.
