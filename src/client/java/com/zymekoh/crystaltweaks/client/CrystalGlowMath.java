package com.zymekoh.crystaltweaks.client;

/** Pure visual curves. A saved 100% remains a multiplier of one, never renormalized to 1/3. */
public final class CrystalGlowMath {
    public static final long FADE_NANOS = 1_200_000_000L;

    private CrystalGlowMath() { }

    public static float power(int percent) { return Math.max(0, Math.min(300, percent)) / 100F; }

    public static float radius(float power) {
        return 0.95F + 0.6F * Math.min(1, power) + 0.22F * Math.max(0, power - 1);
    }

    public static float fade(long elapsedNanos) {
        float t = Math.max(0, Math.min(1, elapsedNanos / (float) FADE_NANOS));
        return 1 - t * t * (3 - 2 * t);
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
