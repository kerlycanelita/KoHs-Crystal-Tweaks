package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/**
 * How much the glow, the motion blur and the reflections draw: the same look with fewer pieces, for
 * a machine or a fight that needs the frames more.
 */
public enum CrystalGlowQuality {
    /** The outer frame and the core only, a short blur, coarse reflections, and nothing far away. */
    PERFORMANCE("Performance", "Rendimiento", false, false, 0, 2, 16.0F, 32.0F, 1, 12.0F, 2),
    /** Every layer, a fuller blur, the reflections as they were. */
    BALANCED("Balanced", "Equilibrado", true, false, 2, 4, 24.0F, 48.0F, 2, 24.0F, 1),
    /** Every layer from both sides, the widest light, the longest and smoothest blur. */
    QUALITY("Quality", "Calidad", true, true, 4, 6, 32.0F, 64.0F, 4, 32.0F, 0);

    private final String english;
    private final String spanish;
    /** Whether the inner frame is lit and blurred, or only the outer frame and the core. */
    public final boolean innerFrame;
    /** Whether the faces turned away from the camera are lit too, seen through the frames. */
    public final boolean farFaces;
    /** The Layers style's aura: how many larger copies. */
    public final int auraCopies;
    public final int blurSteps;
    /** Blocks beyond which the blur is not drawn, and beyond which no light at all is. */
    public final float blurRange;
    public final float lightRange;
    /** How finely a block top is split for the reflections, near; and the blocks they reach. */
    public final int spillCells;
    public final float spillRange;
    /** How many steps coarser the old halo's disc is drawn. */
    public final int haloCoarser;

    CrystalGlowQuality(String english, String spanish, boolean innerFrame, boolean farFaces, int auraCopies, int blurSteps,
            float blurRange, float lightRange, int spillCells, float spillRange, int haloCoarser) {
        this.english = english;
        this.spanish = spanish;
        this.innerFrame = innerFrame;
        this.farFaces = farFaces;
        this.auraCopies = auraCopies;
        this.blurSteps = blurSteps;
        this.blurRange = blurRange;
        this.lightRange = lightRange;
        this.spillCells = spillCells;
        this.spillRange = spillRange;
        this.haloCoarser = haloCoarser;
    }

    public String label(boolean inSpanish) {
        return inSpanish ? this.spanish : this.english;
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public CrystalGlowQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public CrystalGlowQuality previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public static CrystalGlowQuality parse(String key, CrystalGlowQuality fallback) {
        for (CrystalGlowQuality quality : values()) {
            if (quality.storageKey().equalsIgnoreCase(key)) {
                return quality;
            }
        }
        return fallback;
    }
}
