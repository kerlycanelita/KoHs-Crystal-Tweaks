package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/** How the crystal's layers glow. The halo behind the crystal is apart: Old KoHs Crystal Glow. */
public enum CrystalGlowStyle {
    /** A soft light round every lit pixel, laid over the scene: it keeps its colour and never burns out. */
    LIGHT("Light", "Luz"),
    /** The layers drawn again as added light, with an aura: brighter and more saturated. */
    LAYERS("Layers", "Capas");

    private final String english;
    private final String spanish;

    CrystalGlowStyle(String english, String spanish) {
        this.english = english;
        this.spanish = spanish;
    }

    public String label(boolean inSpanish) {
        return inSpanish ? this.spanish : this.english;
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public CrystalGlowStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrystalGlowStyle parse(String key, CrystalGlowStyle fallback) {
        for (CrystalGlowStyle style : values()) {
            if (style.storageKey().equalsIgnoreCase(key)) {
                return style;
            }
        }
        return fallback;
    }
}
