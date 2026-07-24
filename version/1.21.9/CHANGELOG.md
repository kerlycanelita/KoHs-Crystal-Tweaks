# Changelog — 1.21.9

## 2026-04-24

- Split 1.21.9 from the wider compatibility group because it uses the modern renderer and UI APIs.
- Based the port on the validated modern 1.21.10 implementation and adjusted metadata for 1.21.9.
- Added the compatibility handshake and per-server state used by this historical build.
- Replaced an older compatibility channel and displayed a local English notice when a server disabled the feature.
- Refined local damage checks for Weakness without sending additional combat actions.
- Restored a passive join-time compatibility signal.
- Removed the binary-fragile `player.getEntityWorld()` dependency from `SafeCrystalMixin`.
- Fixed the modern renderer `@Inject` by declaring the exact `CameraRenderState` parameter instead of `Object`.
- Fixed the `submitModel` redirect by using the exact `Model` parameter type while keeping the vanilla render-state argument as `Object`.
- Fixed crystal deformation during placement and `Crystal Flotation` by applying the vanilla offset only to `outerGlass`; the hierarchy now moves `innerGlass` and `cube` correctly.
- Updated `fabric.mod.json` to declare `>=1.21.9 <1.21.10`.
- Confirmed a clean `gradlew.bat clean build`.
