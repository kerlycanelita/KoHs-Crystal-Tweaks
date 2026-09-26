package com.zymekoh.crystaltweaks.client;

/**
 * Colours of the Crystal Tweaks screens, in one place: KoHs dark purple glass with the violet and
 * End-cyan of a crystal. Everything is ARGB.
 */
final class CrystalTheme {
    static final int BACKDROP = 0x2604010A;
    static final int PANEL_TOP = 0xC41B0928;
    static final int PANEL_BOTTOM = 0xB40A0310;
    static final int PANEL_BORDER = 0xC6B85BE8;
    static final int CONTENT_TOP = 0x5014081D;
    static final int CONTENT_BOTTOM = 0x3907030D;
    static final int HEADER_LINE = 0x55B85BE8;

    static final int CARD = 0x2C2A0F3A;
    static final int CARD_HOVER = 0x3C3A1552;
    static final int CARD_BORDER = 0x703F1C5C;
    static final int CARD_BORDER_HOVER = 0xB0B067E0;
    static final int LEGEND = 0xFFCDB0E0;

    static final int TEXT = 0xFFF7EDFF;
    static final int TEXT_MUTED = 0xFFC6B5CC;
    static final int TEXT_DISABLED = 0xFFAA98B2;
    static final int TITLE = 0xFFE9D5FF;

    static final int ACCENT = 0xFFD590F3;
    static final int ACCENT_BRIGHT = 0xFFF3D2FF;
    static final int END_CYAN = 0xFF86ECFF;

    static final int STATUS_ACTIVE = 0xFF6BE39A;
    static final int STATUS_PAUSED = 0xFFFFC48A;
    static final int STATUS_CHECKING = 0xFFB98CFF;
    static final int STATUS_PARTIAL = 0xFF8CC8FF;

    static final int SCROLL_TRACK = 0x7A2B1236;
    static final int SCROLL_THUMB = 0xF2C06BE8;

    private CrystalTheme() {
    }

    /** {@code color} with its alpha multiplied by {@code factor}. */
    static int fade(int color, float factor) {
        int alpha = (color >>> 24) & 255;
        int scaled = Math.round(alpha * Math.max(0F, Math.min(1F, factor)));
        return scaled << 24 | (color & 0xFFFFFF);
    }

    static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (color & 0xFFFFFF);
    }

    static int lerp(int from, int to, float amount) {
        float t = Math.max(0F, Math.min(1F, amount));
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static float easeOutCubic(float value) {
        float t = Math.max(0F, Math.min(1F, value));
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }
}
