# Changelog — 1.21.5

## 2026-04-24

- Split 1.21.5 into its own source directory.
- Fixed bytecode compatibility after `ModelPart` stopped using `pivotY` and child transforms no longer used `rotate(MatrixStack)`.
- Updated `EndCrystalEntityModelAnimationMixin` to use `originY` and `EndCrystalEntityRendererSeamlessMixin` to use `applyTransform(...)` for tinted child parts.
- Fixed an unstable player-world call in `SafeCrystalMixin` by reading `client.world`, preventing the reported 1.21.5 `NoSuchMethodError`.
- Updated the configuration screen to use the `OrderedText` path compatible with the 1.21.5 build target.
- Updated `fabric.mod.json` to declare `>=1.21.5 <1.21.6`.
- Confirmed a clean `gradlew.bat clean build`.
