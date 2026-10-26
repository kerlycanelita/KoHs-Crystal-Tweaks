package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;

/**
 * The grimoire's look: a dark book of three pages, gothic titles, rows with dotted leaders and
 * plates for their values. The colours and the shapes every part of the settings shares.
 */
public final class GrimoireDraw {
    public static final int COVER = 0xFF2B2433;
    public static final int PAGE = 0xFF110C17;
    public static final int PAGE_DEEP = 0xFF0C0811;
    public static final int SPINE = 0xFF0A070D;
    public static final int BORDER = 0xFF3A3048;
    public static final int RULE_INNER = 0xFF1C1524;
    public static final int RULE = 0xFF2A2236;
    public static final int RULE_TITLE = 0xFF4A3D5C;
    public static final int TITLE = 0xFFF4EEFA;
    public static final int TEXT = 0xFFEDE6F5;
    public static final int NAV = 0xFFBFB2CF;
    public static final int MUTED = 0xFFA99BB8;
    public static final int FAINT = 0xFF83769A;
    public static final int VALUE = 0xFFE6D2FF;
    public static final int ACCENT = 0xFFA855F7;
    public static final int ACCENT_FILL = 0xFF2A1846;
    public static final int TRACK = 0xFF4A3D5C;
    public static final int DOTS = 0xFF3A3048;
    public static final int CRIMSON = 0xFF6B2135;
    public static final int CRIMSON_FILL = 0xFF1B0A11;
    public static final int CRIMSON_TEXT = 0xFFFFC2D0;

    /**
     * Whether the settings are being drawn, so the hub's controls take the grimoire's look and
     * letters; the other screens that share them keep theirs.
     * ponytail: one flag the settings screen raises around its own drawing; a per-widget theme if a second book appears.
     */
    public static boolean active;

    private GrimoireDraw() {
    }

    /** The font the hub's controls draw with: the grimoire's while the settings are drawn. */
    public static Font font() {
        return active ? GrimoireFonts.font() : Minecraft.getInstance().font;
    }

    /** The cover under the pages, with a shadow round it. */
    public static void cover(GuiGraphicsExtractor graphics, Rect book, float alpha) {
        if (book.isEmpty() || alpha <= 0.01F) {
            return;
        }
        HubDraw.halo(graphics, book.x(), book.y() + 3, book.width(), book.height(), 0xFF000000, 10, 0.75F * alpha);
        graphics.fill(book.x(), book.y(), book.right(), book.bottom(), CrystalTheme.fade(COVER, alpha));
        // A worn edge: lighter along the top, darker along the bottom.
        graphics.fill(book.x() + 1, book.y(), book.right() - 1, book.y() + 1, CrystalTheme.fade(0xFF3A3243, alpha));
        graphics.fill(book.x() + 1, book.bottom() - 1, book.right() - 1, book.bottom(), CrystalTheme.fade(0xFF1A1520, alpha));
    }

    /** One page: its paper, its border and a second rule set inside it. */
    public static void page(GuiGraphicsExtractor graphics, Rect page, int paper, float alpha) {
        if (page.isEmpty() || alpha <= 0.01F) {
            return;
        }
        graphics.fill(page.x(), page.y(), page.right(), page.bottom(), CrystalTheme.fade(paper, alpha));
        CrystalUi.outline(graphics, page.x(), page.y(), page.width(), page.height(), CrystalTheme.fade(BORDER, alpha));
        int inset = inset(page);
        CrystalUi.outline(graphics, page.x() + inset, page.y() + inset, page.width() - inset * 2, page.height() - inset * 2,
                CrystalTheme.fade(RULE_INNER, alpha));
    }

    /** How far inside a page's edge its inner rule runs. */
    public static int inset(Rect page) {
        return page.width() > 60 && page.height() > 60 ? 4 : 2;
    }

    /** The gap between two pages: dark, shaded at both edges. */
    public static void spine(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, CrystalTheme.fade(SPINE, alpha));
        // Darker towards both pages, where the paper curls into the binding.
        for (int column = 0; column < width; column++) {
            float edge = Math.abs(column - (width - 1) / 2.0F) / Math.max(1.0F, (width - 1) / 2.0F);
            int shade = Math.round(150 * edge * edge * alpha);
            if (shade > 4) {
                graphics.fill(x + column, y, x + column + 1, y + height, CrystalTheme.withAlpha(0x000000, shade));
            }
        }
    }

    /** A dotted leader from {@code x0} to {@code x1}, a dot every other pixel: one blit of a repeating dotted strip. */
    public static void dots(GuiGraphicsExtractor graphics, int x0, int x1, int y, int color) {
        if (x1 - x0 < 4 || ((color >>> 24) & 255) < 6) {
            return;
        }
        // The strip starts on an even pixel wherever the leader does, so dots of every row line up.
        graphics.blit(RenderPipelines.GUI_TEXTURED, GrimoireIcons.dots(), x0, y, x0 & 1, 0.0F, x1 - x0, 1, GrimoireIcons.DOTS_LENGTH, 1, color);
    }

    /** A plate: a flat fill with a one-pixel border, the shape of every value and button in the book. */
    public static void plate(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, CrystalTheme.fade(fill, alpha));
        CrystalUi.outline(graphics, x, y, width, height, CrystalTheme.fade(border, alpha));
    }

    /*
     * How far the letters reach from the baseline at scale 1, in GUI pixels, measured from the fonts
     * themselves (capitals and ascenders above, descenders below). The game draws text with its
     * baseline 7 pixels under the y it is given.
     */
    private static final float BASELINE = 7.0F;
    private static final float GOTHIC_ABOVE = 8.6F;
    private static final float GOTHIC_BELOW = 2.8F;
    private static final float TEXT_ABOVE = 8.0F;
    private static final float TEXT_BELOW = 2.8F;
    /** From one line of the book's text to the next: its letters reach 10.8 pixels, so 12 keeps them apart. */
    public static final int LINE = 12;

    /** The clear room kept between letters and a box's border, above and below. */
    public static int margin(int height) {
        return height >= 16 ? 2 : 1;
    }

    /** The largest scale, up to {@code max}, at which the gothic letters fit a box {@code height} tall with their margin. */
    public static float gothicFit(int height, float max) {
        return Math.min(max, (height - 2 - margin(height) * 2) / (GOTHIC_ABOVE + GOTHIC_BELOW));
    }

    /** The same for the plain letters. */
    public static float textFit(int height, float max) {
        return Math.min(max, (height - 2 - margin(height) * 2) / (TEXT_ABOVE + TEXT_BELOW));
    }

    /** The y to draw gothic letters at {@code scale} from, so their ink sits centred in a box at {@code boxY}. */
    public static float gothicY(int boxY, int height, float scale) {
        return boxY + (height - (GOTHIC_ABOVE + GOTHIC_BELOW) * scale) / 2.0F - (BASELINE - GOTHIC_ABOVE) * scale;
    }

    /** The same for the plain letters. */
    public static float textY(int boxY, int height, float scale) {
        return boxY + (height - (TEXT_ABOVE + TEXT_BELOW) * scale) / 2.0F - (BASELINE - TEXT_ABOVE) * scale;
    }

    /** From one line to the next in {@code font}: the book's letters need more room than the game's. */
    public static int line(Font font) {
        return font == GrimoireFonts.font() ? LINE : font.lineHeight + 1;
    }

    /** Plain text at {@code scale}, its top left at (x, y). */
    public static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int color) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** The gothic letters' width at {@code scale}. */
    public static int gothicWidth(Font font, String text, float scale) {
        return Math.round((scale > 1.3F ? GrimoireFonts.gothicLargeWidth(text) : GrimoireFonts.gothicWidth(text)) * scale);
    }

    /** Gothic text at {@code scale}, its top left at (x, y); a soft violet glow behind it when {@code glow} is above 0. */
    public static void gothic(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int color, float glow) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        FormattedCharSequence sequence = scale > 1.3F ? GrimoireFonts.gothicLarge(text) : GrimoireFonts.gothic(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        int haloAlpha = Math.round(70 * glow * ((color >>> 24) & 255) / 255.0F);
        if (haloAlpha >= 8) {
            // A glow, not an outline: the copies sit under a GUI pixel away whatever the scale.
            int halo = CrystalTheme.withAlpha(ACCENT, haloAlpha);
            float reach = 0.7F / scale;
            for (int side = 0; side < 4; side++) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(side == 0 ? -reach : side == 1 ? reach : 0.0F, side == 2 ? -reach : side == 3 ? reach : 0.0F);
                graphics.text(font, sequence, 0, 0, halo, false);
                graphics.pose().popMatrix();
            }
        }
        graphics.text(font, sequence, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Gothic text centred on {@code centerX}. */
    public static void gothicCentered(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float y, float scale, int color,
            float glow) {
        gothic(graphics, font, text, centerX - gothicWidth(font, text, scale) / 2.0F, y, scale, color, glow);
    }

    /** The largest scale, up to {@code max}, at which {@code text} fits in {@code width}. */
    public static float fitScale(Font font, String text, int width, float max) {
        int natural = Math.max(1, GrimoireFonts.gothicWidth(text));
        return Math.max(0.6F, Math.min(max, width / (float) natural));
    }
}
