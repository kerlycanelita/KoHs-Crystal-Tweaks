# Changelog — 1.21.9

## 2026-04-24

- Split 1.21.9 from the wider compatibility group because it uses the modern renderer and UI APIs.
- Based the port on the validated modern 1.21.10 implementation and adjusted metadata for 1.21.9.
- Integrated the useful, legitimate portion of Marlow's Crystal Optimizer with modern `marlowcrystal:version`, `marlowcrystal:opt_out`, and `marlowcrystal:opt_out_ack` identifiers.
- Replaced the legacy `minecraft:mco` channel, added per-server state for persistent opt-out handling, and displayed a local English notice when a server disables the optimizer.
- Reused Marlow's local damage checks to handle Weakness more accurately without sending additional packets or automating combat.
- Restored `OptOutPacket` as a passive join-time compatibility signal.
- Removed the binary-fragile `player.getEntityWorld()` dependency from `SafeCrystalMixin`.
- Fixed the modern renderer `@Inject` by declaring the exact `CameraRenderState` parameter instead of `Object`.
- Fixed the `submitModel` redirect by using the exact `Model` parameter type while keeping the vanilla render-state argument as `Object`.
- Fixed crystal deformation during placement and `Crystal Flotation` by applying the vanilla offset only to `outerGlass`; the hierarchy now moves `innerGlass` and `cube` correctly.
- Updated `fabric.mod.json` to declare `>=1.21.9 <1.21.10`.
- Confirmed a clean `gradlew.bat clean build`.
