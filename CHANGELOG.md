# Changelog

## 1.1.0-beta.1 — 2026-07-12

### Minecraft 1.21.10, 1.21.11, and 26.1.2

- Integrated KoHs Crystal Placement Fix into KoHs Crystal Tweaks.
- Added persistent `placementFixEnabled=true` configuration.
- Added the `Placement Fix` toggle to `Tweaks`, with an `Accept` / `Restore` confirmation when disabling it.
- Limited retargeting to the current vanilla interaction, with no additional packets or automation.
- Moved local prediction to the accepted interaction result.
- Raised the initial adaptive timeout to the configured value.
- Aligned prediction bases with vanilla: obsidian or bedrock.
- Applied per-part tint when queued geometry is actually rendered.

### Minecraft 1.21.10 and 1.21.11

- Replaced fragmented rendering that changed `visible` on queued model parts.
- Made the configuration screen responsive at compact logical sizes and high GUI scales.

### Minecraft 26.1.2

- Ported separate frame/core tint rendering.
- Ported `Spin Speed`, `Crystal Flotation`, and `Static Crystal` to the model and beam.
- Integrated Placement Fix using official Mojang names and Java 25.

## 1.0.0 compatibility builds

- Preserved separate artifacts for 1.21–1.21.1, 1.21.2–1.21.4, 1.21.5, 1.21.6–1.21.8, and 1.21.9.
- Published those verified artifacts through the GitHub pre-release channel without changing their internal mod versions.

