package com.zymekoh.crystaltweaks.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing primitives shared by the Crystal Tweaks screens. Decoration only: nothing here reads or
 * moves a widget, so animating any of it can never pull a hitbox away from what is drawn.
 */
final class CrystalUi {
    private CrystalUi() {
    }

    /** A panel with 4px rounded corners and a vertical gradient. */
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.min(4, Math.min(width / 2, height / 2));
        graphics.fillGradient(x + radius, y, x + width - radius, y + height, top, bottom);
        graphics.fillGradient(x, y + radius, x + width, y + height - radius, top, bottom);
        if (radius == 4) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + 2, top);
            graphics.fill(x + 1, y + 2, x + width - 1, y + 4, top);
            graphics.fill(x + 1, y + height - 4, x + width - 1, y + height - 2, bottom);
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, bottom);
        }
    }

    static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        roundedOutline(graphics, x, y, width, height, color, 0, 0);
    }

    /** A rounded outline whose top edge leaves out {@code gapStart..gapEnd}, for a title. */
    static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color,
            int gapStart, int gapEnd) {
        if (width < 9 || height < 9) {
            outline(graphics, x, y, width, height, color);
            return;
        }
        int right = x + width;
        int bottom = y + height;
        if (gapEnd > gapStart) {
            graphics.fill(x + 4, y, Math.max(x + 4, gapStart), y + 1, color);
            graphics.fill(Math.min(right - 4, gapEnd), y, right - 4, y + 1, color);
        } else {
            graphics.fill(x + 4, y, right - 4, y + 1, color);
        }
        graphics.fill(x + 2, y + 1, x + 4, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 4, color);
        graphics.fill(x, y + 4, x + 1, bottom - 4, color);
        graphics.fill(right - 4, y + 1, right - 2, y + 2, color);
        graphics.fill(right - 2, y + 2, right - 1, y + 4, color);
        graphics.fill(right - 1, y + 4, right, bottom - 4, color);
        graphics.fill(x + 1, bottom - 4, x + 2, bottom - 2, color);
        graphics.fill(x + 2, bottom - 2, x + 4, bottom - 1, color);
        graphics.fill(x + 4, bottom - 1, right - 4, bottom, color);
        graphics.fill(right - 2, bottom - 4, right - 1, bottom - 2, color);
        graphics.fill(right - 4, bottom - 2, right - 2, bottom - 1, color);
    }

    static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * Text that honours a faded colour. Minecraft draws a nearly transparent text colour fully
     * opaque, so anything faded below visibility is simply skipped instead.
     */
    static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        graphics.text(font, text, x, y, color, false);
    }

    static void centered(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, int color) {
        label(graphics, font, text, centerX - font.width(text) / 2, y, color);
    }

    /** Two points of light running around a panel's edge, each trailing a fading tail. */
    static void comets(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double seconds, int color) {
        int perimeter = 2 * (width + height);
        if (width < 12 || height < 12 || perimeter <= 0) {
            return;
        }
        double speed = perimeter / 7.0D;
        for (int comet = 0; comet < 2; comet++) {
            double head = (seconds * speed + comet * perimeter / 2.0D) % perimeter;
            for (int step = 0; step < 26; step++) {
                double position = head - step * 1.6D;
                if (position < 0) {
                    position += perimeter;
                }
                int[] point = perimeterPoint(x, y, width, height, position);
                int alpha = Math.round(210.0F * (1.0F - step / 26.0F));
                int size = step < 4 ? 2 : 1;
                graphics.fill(point[0], point[1], point[0] + size, point[1] + size,
                        CrystalTheme.withAlpha(color, alpha));
            }
        }
    }

    private static int[] perimeterPoint(int x, int y, int width, int height, double position) {
        int p = (int) position;
        if (p < width) {
            return new int[] {x + p, y};
        }
        p -= width;
        if (p < height) {
            return new int[] {x + width - 1, y + p};
        }
        p -= height;
        if (p < width) {
            return new int[] {x + width - 1 - p, y + height - 1};
        }
        p -= width;
        return new int[] {x, y + height - 1 - Math.min(p, height - 1)};
    }

    /** A soft band of light sweeping left to right across an area every few seconds. */
    static void shimmer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, double seconds) {
        double period = 5.0D;
        double sweep = 0.9D;
        double phase = seconds % period;
        if (phase > sweep || x1 <= x0) {
            return;
        }
        int band = 18;
        int center = x0 - band + (int) Math.round((x1 - x0 + band * 2) * (phase / sweep));
        for (int column = -band; column <= band; column++) {
            int x = center + column;
            if (x < x0 || x >= x1) {
                continue;
            }
            float strength = 1.0F - Math.abs(column) / (float) band;
            graphics.fill(x, y0, x + 1, y1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(70 * strength * strength)));
        }
    }

    /**
     * A card behind a group of controls. With a legend, the title is set into the top edge the way
     * a fieldset's is, so it costs no row of its own.
     */
    static void card(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
            String legend, float hover, float alpha) {
        if (alpha <= 0.01F || width <= 0 || height <= 0) {
            return;
        }
        panel(graphics, x, y, width, height,
                CrystalTheme.fade(CrystalTheme.lerp(CrystalTheme.CARD, CrystalTheme.CARD_HOVER, hover), alpha),
                CrystalTheme.fade(CrystalTheme.lerp(0x1A12061E, 0x281A0A2E, hover), alpha));
        int border = CrystalTheme.fade(
                CrystalTheme.lerp(CrystalTheme.CARD_BORDER, CrystalTheme.CARD_BORDER_HOVER, hover), alpha);
        if (legend == null || legend.isEmpty()) {
            roundedOutline(graphics, x, y, width, height, border);
            return;
        }
        int textWidth = font.width(legend);
        int textX = x + 9;
        // The top edge stops short of the title and resumes after it, so the title sits in a real
        // gap instead of on a line painted over the card.
        int gapStart = textX - 3;
        int gapEnd = Math.min(x + width - 4, textX + textWidth + 3);
        roundedOutline(graphics, x, y, width, height, border, gapStart, gapEnd);
        label(graphics, font, legend, textX, y - 4,
                CrystalTheme.fade(CrystalTheme.lerp(CrystalTheme.LEGEND, CrystalTheme.ACCENT_BRIGHT, hover), alpha));
    }

    /** The Crystal Tweaks mark: a small faceted crystal that breathes and turns its highlight. */
    static void crystalIcon(GuiGraphicsExtractor graphics, int centerX, int centerY, int size, double seconds, float alpha) {
        int half = Math.max(2, size / 2);
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * 2.4D);
        int outer = CrystalTheme.fade(CrystalTheme.lerp(0xFFB064F0, 0xFFE3B8FF, pulse), alpha);
        int inner = CrystalTheme.fade(CrystalTheme.lerp(0xFF6FD8F0, 0xFFC6F6FF, 1 - pulse), alpha);
        for (int row = -half; row <= half; row++) {
            int span = half - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, outer);
        }
        int core = Math.max(1, half / 2);
        for (int row = -core; row <= core; row++) {
            int span = core - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, inner);
        }
        // A glint that travels up the left facet.
        int glint = (int) Math.round((seconds * 3.0D) % (half * 2 + 6)) - half;
        if (glint >= -half && glint <= half) {
            int span = half - Math.abs(glint);
            graphics.fill(centerX - span, centerY + glint, centerX - span + 1, centerY + glint + 1,
                    CrystalTheme.fade(0xFFFFFFFF, alpha));
        }
    }

    /** Pixel icons for tabs and arrows, drawn in {@code color} inside a 7x7 cell at (x, y). */
    static void icon(GuiGraphicsExtractor graphics, PurpleCloseButton.Icon icon, int x, int y, int color) {
        switch (icon) {
            case CRYSTAL -> {
                for (int row = 0; row < 7; row++) {
                    int span = 3 - Math.abs(3 - row);
                    graphics.fill(x + 3 - span, y + row, x + 4 + span, y + row + 1, color);
                }
            }
            case GLOW -> {
                graphics.fill(x + 2, y + 2, x + 5, y + 5, color);
                graphics.fill(x + 3, y, x + 4, y + 7, color);
                graphics.fill(x, y + 3, x + 7, y + 4, color);
                graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
                graphics.fill(x + 5, y + 1, x + 6, y + 2, color);
                graphics.fill(x + 1, y + 5, x + 2, y + 6, color);
                graphics.fill(x + 5, y + 5, x + 6, y + 6, color);
            }
            case SOUND -> {
                graphics.fill(x, y + 2, x + 2, y + 5, color);
                graphics.fill(x + 2, y + 1, x + 3, y + 6, color);
                graphics.fill(x + 3, y, x + 4, y + 7, color);
                graphics.fill(x + 5, y + 2, x + 6, y + 5, color);
                graphics.fill(x + 6, y + 1, x + 7, y + 2, color);
                graphics.fill(x + 6, y + 5, x + 7, y + 6, color);
            }
            case GEAR -> {
                graphics.fill(x + 2, y + 1, x + 5, y + 6, color);
                graphics.fill(x + 1, y + 2, x + 6, y + 5, color);
                graphics.fill(x + 3, y, x + 4, y + 7, color);
                graphics.fill(x, y + 3, x + 7, y + 4, color);
                graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFF1B0928);
            }
            case ARROW_LEFT -> {
                for (int column = 0; column < 4; column++) {
                    graphics.fill(x + 1 + column, y + 3 - column, x + 2 + column, y + 4 + column, color);
                }
            }
            case ARROW_RIGHT -> {
                for (int column = 0; column < 4; column++) {
                    graphics.fill(x + 5 - column, y + 3 - column, x + 6 - column, y + 4 + column, color);
                }
            }
            default -> { }
        }
    }

    /** A tooltip box of our own, for areas that are not widgets and so cannot carry a vanilla one. */
    static void tooltip(GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines,
            int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * (font.lineHeight + 1) - 1;
        int x = Math.min(mouseX + 10, screenWidth - width - 8);
        int y = Math.min(mouseY + 10, screenHeight - height - 8);
        x = Math.max(4, x);
        y = Math.max(4, y);
        graphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0xF0120616);
        graphics.fillGradient(x - 4, y - 4, x + width + 4, y - 3, 0xC0B067E0, 0xC0B067E0);
        graphics.fillGradient(x - 4, y + height + 3, x + width + 4, y + height + 4, 0xA05A2A80, 0xA05A2A80);
        graphics.fillGradient(x - 4, y - 3, x - 3, y + height + 3, 0xC0B067E0, 0xA05A2A80);
        graphics.fillGradient(x + width + 3, y - 3, x + width + 4, y + height + 3, 0xC0B067E0, 0xA05A2A80);
        int lineY = y;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x, lineY, CrystalTheme.TEXT, false);
            lineY += font.lineHeight + 1;
        }
    }

    /** Wraps {@code text} to {@code width} with Minecraft's own line breaker. */
    static List<FormattedCharSequence> wrap(Font font, String text, int width) {
        return font.split(Component.literal(text), Math.max(20, width));
    }

    /** Motes of violet light drifting up the whole screen, their number scaled to its area. */
    static void floatingParticles(GuiGraphicsExtractor graphics, int width, int height, double seconds) {
        int span = Math.max(1, height + 24);
        int count = Math.max(56, Math.min(110, width * height / 4_000));
        for (int index = 0; index < count; index++) {
            double phase = index * 0.61803398875D;
            double speed = 4.5D + index % 7;
            int baseX = Math.floorMod(index * 83 + 29, Math.max(1, width));
            int x = baseX + (int) Math.round(Math.sin(seconds * 0.62D + phase) * (4 + index % 6));
            int y = height + 8 - (int) ((seconds * speed + phase * span) % span);
            int size = index % 6 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            int alpha = 84 + index % 6 * 12;
            int green = 82 + index % 4 * 18;
            int glow = alpha / 4 << 24 | 164 << 16 | 58 << 8 | 231;
            int color = alpha << 24 | 210 << 16 | green << 8 | 255;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, glow);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    /** A filled ellipse from horizontal spans, for flat shapes such as the preview's pedestal. */
    static void ellipse(GuiGraphicsExtractor graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
        if (radiusX <= 0 || radiusY <= 0) {
            return;
        }
        for (int row = -radiusY; row <= radiusY; row++) {
            double t = row / (double) radiusY;
            int span = (int) Math.round(radiusX * Math.sqrt(Math.max(0.0D, 1.0D - t * t)));
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, color);
        }
    }

    /** Just the rim of an ellipse, one pixel wide. */
    static void ellipseRim(GuiGraphicsExtractor graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
        if (radiusX <= 0 || radiusY <= 0) {
            return;
        }
        for (int row = -radiusY; row <= radiusY; row++) {
            double t = row / (double) radiusY;
            int span = (int) Math.round(radiusX * Math.sqrt(Math.max(0.0D, 1.0D - t * t)));
            graphics.fill(centerX - span, centerY + row, centerX - span + 1, centerY + row + 1, color);
            graphics.fill(centerX + span, centerY + row, centerX + span + 1, centerY + row + 1, color);
            if (row == -radiusY || row == radiusY) {
                graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, color);
            }
        }
    }
}
