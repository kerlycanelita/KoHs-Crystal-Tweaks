package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;

/**
 * The colours of the settings hub: Zymekoh purple for your own crystals, crimson for the enemy
 * profile, and on top of either the light of the crystal being edited.
 *
 * <p>The side panels take on the glow colour while it is being changed, as if lit by the crystal
 * beside them, and only up to a point: their surfaces move a little toward it, their borders more,
 * and nothing else on the screen does. The screen refreshes this once per frame; widgets read it
 * while drawing, so a control always wears the same light as the panel it sits on.</p>
 */
public final class HubSkin {
    /** Both palettes, as fixed ARGB values. */
    private static final int[] USER = {
            0xB40B0614, 0xE205020A, // backdrop top, bottom
            0xD41A0A2A, 0xC80B0414, // panel top, bottom
            0xC8A75BE0, 0x703F1C5C, // border, soft border
            0xFFC084FC, 0xFFEBD7FF, // accent, bright accent
            0xFFE83EAF, // hot magenta
            0xFFF7EDFF, 0xFFC6B5CC, 0xFF8E7C98, // text, muted, disabled
            0xFFCDB0E0, 0xFFE9D5FF, // legend, title
            0xB53A1748, 0xD15D2877, // control fill, control hover
            0x9C9A62C8, // branch
    };
    private static final int[] ENEMY = {
            0xB4120509, 0xE2070203,
            0xD42A0A12, 0xC8120407,
            0xC8E04A5C, 0x705C1C26,
            0xFFFF5A6E, 0xFFFFD0D6,
            0xFFFF315C,
            0xFFFFF1F3, 0xFFD9B6BC, 0xFF9C7F84,
            0xFFF0B8C0, 0xFFFFD5DA,
            0xB5481420, 0xD177283A,
            0x9CC8626E,
    };

    private static final HubSkin CURRENT = new HubSkin();

    public boolean enemy;
    /** The colour of the crystal's light, and how strongly the panels show it, 0 to 1. */
    public int halo = 0xFFC880FF;
    public float influence;

    public int backdropTop;
    public int backdropBottom;
    public int panelTop;
    public int panelBottom;
    public int border;
    public int borderSoft;
    public int accent;
    public int accentBright;
    public int hot;
    public int text;
    public int muted;
    public int disabled;
    public int legend;
    public int title;
    public int controlFill;
    public int controlHover;
    public int branch;
    /** Surfaces and borders of the side panels, lit by the glow. */
    public int sideTop;
    public int sideBottom;
    public int sideBorder;

    private HubSkin() {
        update(false, this.halo, 0.0F);
    }

    public static HubSkin current() {
        return CURRENT;
    }

    /**
     * @param influence how much of the glow reaches the side panels and their controls, 0 to 1;
     *                  the screen keeps it low at rest and raises it while the glow is edited
     */
    public void update(boolean enemyProfile, int haloColor, float influence) {
        int[] palette = enemyProfile ? ENEMY : USER;
        this.enemy = enemyProfile;
        this.halo = haloColor | 0xFF000000;
        this.influence = HubMotion.clamp01(influence);
        this.backdropTop = palette[0];
        this.backdropBottom = palette[1];
        this.panelTop = palette[2];
        this.panelBottom = palette[3];
        this.border = palette[4];
        this.borderSoft = palette[5];
        this.accent = palette[6];
        this.accentBright = palette[7];
        this.hot = palette[8];
        this.text = palette[9];
        this.muted = palette[10];
        this.disabled = palette[11];
        this.legend = palette[12];
        this.title = palette[13];
        this.controlFill = palette[14];
        this.controlHover = palette[15];
        this.branch = palette[16];
        // Lit, but never repainted: the surfaces move at most a quarter of the way to the glow and
        // stay dark, the borders up to two thirds, so the palette still reads as purple or red.
        float surface = 0.06F + 0.19F * this.influence;
        float edge = 0.18F + 0.48F * this.influence;
        this.sideTop = tint(this.panelTop, darken(this.halo, 0.32F), surface);
        this.sideBottom = tint(this.panelBottom, darken(this.halo, 0.18F), surface * 0.8F);
        this.sideBorder = tint(this.border, this.halo, edge);
    }

    /** A control's accent on a side panel: the theme accent, partly lit by the glow. */
    public int litAccent() {
        return tint(this.accent, this.halo, 0.12F + 0.38F * this.influence);
    }

    public int litControlFill(float hover) {
        int base = CrystalTheme.lerp(this.controlFill, this.controlHover, hover);
        return tint(base, darken(this.halo, 0.45F), 0.05F + 0.22F * this.influence);
    }

    /** {@code color} moved toward {@code target} by {@code amount}, keeping its own alpha. */
    public static int tint(int color, int target, float amount) {
        int alpha = color >>> 24;
        int mixed = CrystalTheme.lerp(color | 0xFF000000, target | 0xFF000000, amount);
        return alpha << 24 | (mixed & 0xFFFFFF);
    }

    public static int darken(int color, float brightness) {
        int red = Math.round(((color >> 16) & 255) * brightness);
        int green = Math.round(((color >> 8) & 255) * brightness);
        int blue = Math.round((color & 255) * brightness);
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
