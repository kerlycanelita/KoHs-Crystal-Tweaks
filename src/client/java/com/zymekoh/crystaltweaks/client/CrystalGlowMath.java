package com.zymekoh.crystaltweaks.client;

/** Pure visual curves. A saved 100% remains a multiplier of one, never renormalized to 1/3. */
public final class CrystalGlowMath {
    public static final long FADE_NANOS = 1_200_000_000L;

    /** Power the flash was drawn with in 2.3.0 at the shipped glow setting, and its brightest. */
    private static final float SHIPPED_FLASH_POWER = 0.55F;
    private static final float MAX_FLASH_POWER = 3F;

    /**
     * Makes 50% opacity reproduce 2.3.0's default flash and 100% its brightest (the old 300% power).
     * Perceived brightness is not linear in additive alpha, so a straight line would crowd every
     * useful value into the bottom fifth of the slider.
     */
    private static final double FLASH_CURVE = Math.log(SHIPPED_FLASH_POWER / MAX_FLASH_POWER) / Math.log(0.5D);

    /** Radius of a flash at 100% size: the size 2.3.0 gave it at the shipped glow power. */
    public static final float FLASH_BASE_RADIUS = radius(SHIPPED_FLASH_POWER);

    private CrystalGlowMath() { }

    public static float power(int percent) { return Math.max(0, Math.min(300, percent)) / 100F; }

    public static float radius(float power) {
        return 0.95F + 0.6F * Math.min(1, power) + 0.22F * Math.max(0, power - 1);
    }

    /** Alpha gain of a flash at {@code opacityPercent}, from 0 to the old 300% power. */
    public static float flashGain(int opacityPercent) {
        float opacity = Math.max(0, Math.min(100, opacityPercent)) / 100F;
        return (float) (MAX_FLASH_POWER * Math.pow(opacity, FLASH_CURVE));
    }

    /** The opacity that draws a flash as bright as {@code power} did before it had its own slider. */
    public static int opacityForPower(float power) {
        float clamped = Math.max(0F, Math.min(MAX_FLASH_POWER, power));
        return Math.round((float) (100D * Math.pow(clamped / MAX_FLASH_POWER, 1D / FLASH_CURVE)));
    }

    public static float fade(long elapsedNanos) {
        return fade(elapsedNanos, FADE_NANOS);
    }

    public static float fade(long elapsedNanos, long durationNanos) {
        float t = Math.max(0, Math.min(1, elapsedNanos / (float) Math.max(1L, durationNanos)));
        return 1 - t * t * (3 - 2 * t);
    }

    /** How far a flash has faded, 0 at the explosion and 1 when it is gone. */
    public static float progress(long elapsedNanos) {
        return progress(elapsedNanos, FADE_NANOS);
    }

    public static float progress(long elapsedNanos, long durationNanos) {
        return Math.max(0, Math.min(1, elapsedNanos / (float) Math.max(1L, durationNanos)));
    }

    public static int alpha(float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255);
    }

    public static int blockLight(int power) { return Math.round(15F * Math.min(1, power(power))) << 4; }

    public static int hotColor(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int peak = Math.max(r, Math.max(g, b));
        return 0xFF000000 | Math.round(r * 0.22F + peak * 0.78F) << 16
                | Math.round(g * 0.22F + peak * 0.78F) << 8 | Math.round(b * 0.22F + peak * 0.78F);
    }
}
