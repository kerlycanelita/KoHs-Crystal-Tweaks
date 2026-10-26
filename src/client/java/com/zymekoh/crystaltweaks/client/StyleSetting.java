package com.zymekoh.crystaltweaks.client;

/**
 * One of a glow style's or a flash shape's own settings, kept for that style or shape alone: each
 * keeps its own when another is chosen. Read by the renderer every frame, written by the settings
 * screen, saved with the rest of the glow's settings.
 */
public final class StyleSetting {
    /** What a setting counts. */
    public enum Unit {
        PERCENT, BEATS, COUNT, DEGREES
    }

    private final String key;
    private final String english;
    private final String spanish;
    private final String englishTip;
    private final String spanishTip;
    private final int min;
    private final int max;
    private final int fallback;
    private final Unit unit;
    private int value;

    StyleSetting(String key, String english, String spanish, String englishTip, String spanishTip, int min, int max, int fallback,
            Unit unit) {
        this.key = key;
        this.english = english;
        this.spanish = spanish;
        this.englishTip = englishTip;
        this.spanishTip = spanishTip;
        this.min = min;
        this.max = max;
        this.fallback = fallback;
        this.unit = unit;
        this.value = fallback;
    }

    public String key() {
        return this.key;
    }

    public String label(boolean inSpanish) {
        return inSpanish ? this.spanish : this.english;
    }

    public String tip(boolean inSpanish) {
        return inSpanish ? this.spanishTip : this.englishTip;
    }

    public int min() {
        return this.min;
    }

    public int max() {
        return this.max;
    }

    public int fallback() {
        return this.fallback;
    }

    public int value() {
        return this.value;
    }

    /** The value as a fraction of 100%: 1 where it starts, for the percentages. */
    public float factor() {
        return this.value / 100.0F;
    }

    /** The value in radians, for the angles. */
    public float radians() {
        return (float) Math.toRadians(this.value);
    }

    public void set(int value) {
        this.value = Math.max(this.min, Math.min(this.max, value));
    }

    /** {@code value} as the slider shows it. */
    public String shown(int value, boolean inSpanish) {
        return switch (this.unit) {
            case PERCENT -> value + "%";
            case BEATS -> value + (inSpanish ? " lpm" : " bpm");
            case COUNT -> String.valueOf(value);
            case DEGREES -> value + "°";
        };
    }
}
