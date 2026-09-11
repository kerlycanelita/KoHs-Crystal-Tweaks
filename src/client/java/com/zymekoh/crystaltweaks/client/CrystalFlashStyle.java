package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/**
 * Shape the death flash takes. Purely a drawing choice: the light, its colour, its duration and the
 * ground spill are the same whichever one is selected, and none of them touch an entity or a packet.
 */
public enum CrystalFlashStyle {
    /** The original burst: stacked discs with rotating facet rays. */
    EXPLOSION("Explosion", "Explosion"),
    SKULL("Calavera", "Skull"),
    STEVE("Cabeza de Steve", "Steve head"),
    /** Bolts thrown from the blast toward the players around it. */
    LIGHTNING("Rayos", "Lightning");

    private final String spanish;
    private final String english;

    CrystalFlashStyle(String spanish, String english) {
        this.spanish = spanish;
        this.english = english;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    public CrystalFlashStyle next() {
        CrystalFlashStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static CrystalFlashStyle parse(String value, CrystalFlashStyle fallback) {
        if (value == null) {
            return fallback;
        }
        for (CrystalFlashStyle style : values()) {
            if (style.name().equalsIgnoreCase(value.trim())) {
                return style;
            }
        }
        return fallback;
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
