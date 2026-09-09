package com.zymekoh.crystaltweaks.client;

/** Visual settings only. Copies travel with each deferred render submission. */
public final class CrystalAppearance {
    public int outerColor = -1;
    public int innerColor = -1;
    public int coreColor = -1;
    public int rotationSpeedPercent = 100;
    public int floatingSpeedPercent = 100;
    public int glowPowerPercent;
    public int glowReflectionsPercent;
    public boolean customGlowColor;
    public int glowColor = -1;

    public CrystalAppearance copy() {
        CrystalAppearance copy = new CrystalAppearance();
        copy.outerColor = outerColor | 0xFF000000;
        copy.innerColor = innerColor | 0xFF000000;
        copy.coreColor = coreColor | 0xFF000000;
        copy.rotationSpeedPercent = clamp(rotationSpeedPercent, 300);
        copy.floatingSpeedPercent = clamp(floatingSpeedPercent, 300);
        copy.glowPowerPercent = clamp(glowPowerPercent, 300);
        copy.glowReflectionsPercent = clamp(glowReflectionsPercent, 300);
        copy.customGlowColor = customGlowColor;
        copy.glowColor = glowColor | 0xFF000000;
        return copy;
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
