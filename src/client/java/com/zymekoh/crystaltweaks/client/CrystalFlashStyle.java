package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/**
 * Shape the death flash takes. Purely a drawing choice: the light, its colour, its duration and the
 * ground spill are the same whichever one is selected, and none of them touch an entity or a packet.
 */
public enum CrystalFlashStyle {
    /** The original burst: stacked discs with rotating facet rays. */
    EXPLOSION("Explosión", "Explosion"),
    SKULL("Calavera", "Skull"),
    /** The player's own head, drawn from their skin as a pale apparition. */
    MY_HEAD("Mi cabeza", "My head"),
    /** Bolts thrown from the blast toward the players around it that the player can see. */
    LIGHTNING("Rayos", "Lightning"),
    HEART("Corazón", "Heart"),
    STAR("Estrella", "Star"),
    /** A ring that expands as the flash fades. */
    SHOCKWAVE("Onda expansiva", "Shockwave"),
    /** Three arms spiralling out of the blast. */
    VORTEX("Vórtice", "Vortex"),
    CROWN("Corona", "Crown"),
    CRESCENT("Luna", "Crescent"),
    SNOWFLAKE("Copo de nieve", "Snowflake"),
    FLOWER("Flor", "Flower"),
    GEM("Gema", "Gem"),
    SWORDS("Espadas", "Swords");

    private final String spanish;
    private final String english;

    CrystalFlashStyle(String spanish, String english) {
        this.spanish = spanish;
        this.english = english;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    /** True for the styles the size slider applies to; the burst keeps its own proportions. */
    public boolean scalable() {
        return this != EXPLOSION;
    }

    public CrystalFlashStyle next() {
        CrystalFlashStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public CrystalFlashStyle previous() {
        CrystalFlashStyle[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static CrystalFlashStyle parse(String value, CrystalFlashStyle fallback) {
        if (value == null) {
            return fallback;
        }
        String wanted = value.trim();
        // 2.3.0 shipped a Steve head briefly; anyone who picked it gets their own head instead.
        if (wanted.equalsIgnoreCase("STEVE")) {
            return MY_HEAD;
        }
        for (CrystalFlashStyle style : values()) {
            if (style.name().equalsIgnoreCase(wanted)) {
                return style;
            }
        }
        return fallback;
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
