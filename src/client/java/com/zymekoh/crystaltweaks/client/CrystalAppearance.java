package com.zymekoh.crystaltweaks.client;

/** Visual settings only. Copies travel with each deferred render submission. */
public final class CrystalAppearance {
    /** Smallest flash size the slider offers, in percent of the natural size. */
    public static final int MIN_FLASH_SCALE = 10;
    /** Faintest flash the slider offers; below this it is invisible, which is what the switch is for. */
    public static final int MIN_FLASH_OPACITY = 10;
    /** The opacity that draws the flash exactly as 2.3.0 did at the shipped glow power. */
    public static final int DEFAULT_FLASH_OPACITY = 50;
    public static final int MIN_FLASH_DURATION = 300;
    public static final int MAX_FLASH_DURATION = 3000;
    public static final int DEFAULT_FLASH_DURATION = 1200;

    public int outerColor = -1;
    public int innerColor = -1;
    public int coreColor = -1;
    public int rotationSpeedPercent = 100;
    public int floatingSpeedPercent = 100;
    /** The glow switch. Off hides the halo, the reflections and the crystal's own light, not the flash. */
    public boolean glowEnabled = true;
    /** Glow ships on: the mod's whole point is visible from the first launch, not after a hunt. */
    public int glowPowerPercent = 55;
    public int glowReflectionsPercent = 55;
    public boolean customGlowColor = true;
    /** Crystal purple for your own; the enemy profile overrides this with red when it is created. */
    public int glowColor = 0xFFC880FF;
    /** The flash a destroyed crystal leaves. Independent of the glow switch. */
    public boolean flashEnabled = true;
    /** Size of the death flash, 10-300, for every style. */
    public int flashScalePercent = 100;
    /** How strong the death flash is, 10-100; see {@link CrystalGlowMath#flashGain}. */
    public int flashOpacityPercent = DEFAULT_FLASH_OPACITY;
    /** How long the death flash takes to fade, in milliseconds. */
    public int flashDurationMillis = DEFAULT_FLASH_DURATION;

    public CrystalAppearance copy() {
        CrystalAppearance copy = new CrystalAppearance();
        copy.outerColor = outerColor | 0xFF000000;
        copy.innerColor = innerColor | 0xFF000000;
        copy.coreColor = coreColor | 0xFF000000;
        copy.rotationSpeedPercent = clamp(rotationSpeedPercent, 300);
        copy.floatingSpeedPercent = clamp(floatingSpeedPercent, 300);
        copy.glowEnabled = glowEnabled;
        copy.glowPowerPercent = clamp(glowPowerPercent, 300);
        copy.glowReflectionsPercent = clamp(glowReflectionsPercent, 300);
        copy.customGlowColor = customGlowColor;
        copy.glowColor = glowColor | 0xFF000000;
        copy.flashEnabled = flashEnabled;
        copy.flashScalePercent = Math.max(MIN_FLASH_SCALE, Math.min(300, flashScalePercent));
        copy.flashOpacityPercent = Math.max(MIN_FLASH_OPACITY, Math.min(100, flashOpacityPercent));
        copy.flashDurationMillis = Math.max(MIN_FLASH_DURATION, Math.min(MAX_FLASH_DURATION, flashDurationMillis));
        return copy;
    }

    /** True when the halo, its reflections and the crystal's own light are drawn. */
    public boolean glowActive() {
        return glowEnabled && glowPowerPercent > 0;
    }

    /** True when a destroyed crystal leaves a flash. */
    public boolean flashActive() {
        return flashEnabled && flashOpacityPercent > 0;
    }

    public float flashGain() {
        return CrystalGlowMath.flashGain(flashOpacityPercent);
    }

    public float flashScale() {
        return Math.max(MIN_FLASH_SCALE, Math.min(300, flashScalePercent)) / 100F;
    }

    public float flashRadius() {
        return CrystalGlowMath.FLASH_BASE_RADIUS * flashScale();
    }

    public long flashDurationNanos() {
        return Math.max(MIN_FLASH_DURATION, Math.min(MAX_FLASH_DURATION, flashDurationMillis)) * 1_000_000L;
    }

    /**
     * Carries a profile saved before the glow and the flash had switches of their own over without
     * changing how it looks.
     *
     * <p>Until 2.3.0 the flash was drawn with the glow's power, and a power of zero was the only way
     * to turn either off. So an old profile's power becomes its flash opacity and, through the
     * radius that power gave the flash, its flash size; a power of zero becomes both switches off.
     * The glow then gets its shipped power back, so switching it on again shows something.</p>
     *
     * @param scalableStyle whether the saved style applied the old size slider; the burst did not
     */
    public void migrateLegacyGlow(boolean hasGlowSwitch, boolean hasFlashSettings, boolean scalableStyle) {
        int storedPower = Math.max(0, Math.min(300, this.glowPowerPercent));
        if (!hasFlashSettings) {
            this.flashEnabled = storedPower > 0;
            if (storedPower > 0) {
                float power = CrystalGlowMath.power(storedPower);
                this.flashOpacityPercent = Math.max(MIN_FLASH_OPACITY, Math.min(100, CrystalGlowMath.opacityForPower(power)));
                float oldRadius = CrystalGlowMath.radius(power) * (scalableStyle ? this.flashScalePercent / 100F : 1F);
                this.flashScalePercent = Math.max(MIN_FLASH_SCALE,
                        Math.min(300, Math.round(100F * oldRadius / CrystalGlowMath.FLASH_BASE_RADIUS)));
            }
        }
        if (!hasGlowSwitch) {
            this.glowEnabled = storedPower > 0;
            if (storedPower == 0) {
                this.glowPowerPercent = 55;
            }
        }
    }

    public int haloColor() {
        if (customGlowColor) return glowColor;
        // Neutral texture tint still gets a crystal-purple halo; no texture pixels are replaced.
        if (outerColor == -1 && innerColor == -1 && coreColor == -1) return 0xFFC880FF;
        return 0xFF000000 | average(16) << 16 | average(8) << 8 | average(0);
    }

    private int average(int shift) {
        int sum = 0, count = 0;
        // Neutral/unpainted layers must not wash out a deliberately saturated custom layer.
        if (outerColor != -1) { sum += (outerColor >>> shift) & 255; count++; }
        if (innerColor != -1) { sum += (innerColor >>> shift) & 255; count++; }
        if (coreColor != -1) { sum += (coreColor >>> shift) & 255; count++; }
        return count == 0 ? 255 : sum / count;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }
}
