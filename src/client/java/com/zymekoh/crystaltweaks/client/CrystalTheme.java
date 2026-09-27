package com.zymekoh.crystaltweaks.client;

/**
 * Colours of the Crystal Tweaks screens, in one place: KoHs dark purple glass with the violet and
 * End-cyan of a crystal. Everything is ARGB.
 */
public final class CrystalTheme {
    public static final int BACKDROP = 0x2604010A;
    public static final int PANEL_TOP = 0xC41B0928;
    public static final int PANEL_BOTTOM = 0xB40A0310;
    public static final int PANEL_BORDER = 0xC6B85BE8;
    public static final int CONTENT_TOP = 0x5014081D;
    public static final int CONTENT_BOTTOM = 0x3907030D;
    public static final int HEADER_LINE = 0x55B85BE8;

    public static final int CARD = 0x2C2A0F3A;
    public static final int CARD_HOVER = 0x3C3A1552;
    public static final int CARD_BORDER = 0x703F1C5C;
    public static final int CARD_BORDER_HOVER = 0xB0B067E0;
    public static final int LEGEND = 0xFFCDB0E0;

    public static final int TEXT = 0xFFF7EDFF;
    public static final int TEXT_MUTED = 0xFFC6B5CC;
    public static final int TEXT_DISABLED = 0xFFAA98B2;
    public static final int TITLE = 0xFFE9D5FF;

    public static final int ACCENT = 0xFFD590F3;
    public static final int ACCENT_BRIGHT = 0xFFF3D2FF;
    public static final int END_CYAN = 0xFF86ECFF;

    public static final int STATUS_ACTIVE = 0xFF6BE39A;
    public static final int STATUS_PAUSED = 0xFFFFC48A;
    public static final int STATUS_CHECKING = 0xFFB98CFF;
    public static final int STATUS_PARTIAL = 0xFF8CC8FF;
    public static final int STATUS_FORCED_OFF = 0xFFB9A6C8;

    /** The line tying a switch to the rows it unfolds. */
    public static final int BRANCH = 0x9C9A62C8;
    public static final int DANGER = 0xFFFF3B4E;
    public static final int DANGER_BRIGHT = 0xFFFFC2C8;

    public static final int SCROLL_TRACK = 0x7A2B1236;
    public static final int SCROLL_THUMB = 0xF2C06BE8;

    private CrystalTheme() {
    }

    /** {@code color} with its alpha multiplied by {@code factor}. */
    public static int fade(int color, float factor) {
        int alpha = (color >>> 24) & 255;
        int scaled = Math.round(alpha * Math.max(0F, Math.min(1F, factor)));
        return scaled << 24 | (color & 0xFFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (color & 0xFFFFFF);
    }

    public static int lerp(int from, int to, float amount) {
        float t = Math.max(0F, Math.min(1F, amount));
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static float easeOutCubic(float value) {
        float t = Math.max(0F, Math.min(1F, value));
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }
}
