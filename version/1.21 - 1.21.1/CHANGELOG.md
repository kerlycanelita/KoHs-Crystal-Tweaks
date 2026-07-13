# Changelog — 1.21–1.21.1

## 2026-04-23

- Validated the shared 1.21 and 1.21.1 source set in a single `1.21 - 1.21.1` directory.
- Confirmed that `gradlew.bat clean build` completes without mixin or remapping errors.
- Reworked vanilla audio replacement as a runtime resource-pack-style override by injecting a virtual `Sound` through `SoundManager` and `SoundLoader` instead of playing OpenAL manually.
- Removed the extra delay caused by parallel playback; custom audio now follows the vanilla explosion timing.
- Removed dependency on crystal death or explosion-position detection. Every vanilla explosion event uses the loaded custom sound.
- Removed `Anchor Charge` so the system focuses exclusively on vanilla explosion replacement.
- Removed residual per-version directories after consolidating the supported range.
- Preserved the existing configuration screen behavior without introducing unwanted vanilla blur.
