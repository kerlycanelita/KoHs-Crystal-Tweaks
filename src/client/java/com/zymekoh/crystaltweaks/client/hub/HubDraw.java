package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

/**
 * Drawing primitives of the settings hub. Decoration only: nothing here reads or moves a widget, so
 * none of it can pull a hitbox away from what is drawn.
 *
 * <p>Minecraft places every GUI element by testing it against the elements already in its stratum,
 * so a shape made of hundreds of small fills makes every later element test all of them. The
 * shapes that are made of many pieces (rings, glows, particle clouds, shards) are drawn in a
 * stratum of their own with {@link #isolate}, which keeps that work linear.</p>
 */
public final class HubDraw {
    private static final Identifier[] DESTROY_STAGES = new Identifier[10];

    static {
        for (int stage = 0; stage < 10; stage++) {
            DESTROY_STAGES[stage] = Identifier.withDefaultNamespace("textures/block/destroy_stage_" + stage + ".png");
        }
    }

    private HubDraw() {
    }

    /** Starts a new GUI stratum: drawn over everything before it. */
    public static void isolate(GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
    }

    public static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    public static Identifier own(String path) {
        return Identifier.fromNamespaceAndPath("crystal_tweaks", path);
    }

    /** A glass panel: rounded, a vertical gradient, a border and a faint highlight under the top edge. */
    public static void glass(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int top, int bottom,
            int border, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        CrystalUi.panel(graphics, x, y, width, height, CrystalTheme.fade(top, alpha), CrystalTheme.fade(bottom, alpha));
        if (width > 10 && height > 6) {
            graphics.fill(x + 4, y + 1, x + width - 4, y + 2, CrystalTheme.fade(0x22FFFFFF, alpha));
        }
        CrystalUi.roundedOutline(graphics, x, y, width, height, CrystalTheme.fade(border, alpha));
    }

    /**
     * A soft light around a rectangle: outlines that grow fainter outward. {@code strength} is the
     * innermost alpha, 0 to 1.
     */
    public static void halo(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int radius,
            float strength) {
        if (strength <= 0.01F || radius <= 0) {
            return;
        }
        for (int step = 1; step <= radius; step++) {
            float falloff = 1.0F - (step - 1) / (float) radius;
            int alpha = Math.round(255 * strength * falloff * falloff * 0.55F);
            if (alpha < 3) {
                continue;
            }
            CrystalUi.outline(graphics, x - step, y - step, width + step * 2, height + step * 2,
                    CrystalTheme.withAlpha(color, alpha));
        }
    }

    /** A filled disc of light that fades out from its centre, in a few rings. */
    public static void glowDisc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color, float strength) {
        if (radius <= 0 || strength <= 0.01F) {
            return;
        }
        int rings = Math.max(3, Math.min(9, radius / 5));
        for (int ring = rings; ring >= 1; ring--) {
            int r = Math.round(radius * ring / (float) rings);
            float t = ring / (float) rings;
            int alpha = Math.round(255 * strength * (1.0F - t) * 0.32F + 6 * strength);
            CrystalUi.ellipse(graphics, centerX, centerY, r, r, CrystalTheme.withAlpha(color, alpha));
        }
    }

    /** A circle's outline as a chain of small squares, cheap at any size. */
    public static void ring(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius, int thickness, int color) {
        if (radius < 1.0F || ((color >>> 24) & 255) < 4) {
            return;
        }
        int points = Math.max(16, Math.min(180, Math.round(radius * 2.4F)));
        for (int index = 0; index < points; index++) {
            double angle = index * Math.PI * 2.0D / points;
            int x = Math.round(centerX + (float) Math.cos(angle) * radius);
            int y = Math.round(centerY + (float) Math.sin(angle) * radius);
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    /** A rectangle turned by {@code angle} radians around its own centre. */
    public static void shard(GuiGraphicsExtractor graphics, float centerX, float centerY, float width, float height, float angle,
            int color) {
        if (((color >>> 24) & 255) < 4 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate(angle);
        graphics.pose().scale(width, height);
        // A unit square, scaled: fill takes integers, so the size lives in the transform.
        graphics.pose().translate(-0.5F, -0.5F);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    /** A straight line of the given thickness between two points. */
    public static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) {
            return;
        }
        shard(graphics, (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, length, thickness, (float) Math.atan2(dy, dx), color);
    }

    /** A four-pointed sparkle. */
    public static void sparkle(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        if (((color >>> 24) & 255) < 6) {
            return;
        }
        graphics.fill(x - size, y, x + size + 1, y + 1, color);
        graphics.fill(x, y - size, x + 1, y + size + 1, color);
        if (size >= 3) {
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    public static void diamond(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int span = radius - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, color);
        }
    }

    /**
     * A texture drawn into {@code size} pixels around a centre, turned by {@code angle}. {@code
     * textureWidth} and {@code textureHeight} are the file's own pixels.
     */
    public static void texture(GuiGraphicsExtractor graphics, Identifier texture, int textureWidth, int textureHeight,
            float centerX, float centerY, float width, float height, float angle, int color) {
        if (((color >>> 24) & 255) < 4 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        if (angle != 0.0F) {
            graphics.pose().rotate(angle);
        }
        graphics.pose().scale(width / textureWidth, height / textureHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -textureWidth / 2, -textureHeight / 2, 0.0F, 0.0F,
                textureWidth, textureHeight, textureWidth, textureHeight, color);
        graphics.pose().popMatrix();
    }

    /** A 16x16 item or block texture, centred and turned. */
    public static void icon(GuiGraphicsExtractor graphics, Identifier texture, float centerX, float centerY, float size,
            float angle, int color) {
        texture(graphics, texture, 16, 16, centerX, centerY, size, size, angle, color);
    }

    /**
     * A block as an isometric cube from its texture: the top face lit, the two sides shaded, and
     * optionally the cracks of a block being mined (stage 0 to 9; below 0 for none).
     *
     * @param size the cube's edge on screen, in pixels
     */
    public static void isoBlock(GuiGraphicsExtractor graphics, Identifier top, Identifier side, float centerX, float centerY,
            float size, int crackStage, float alpha) {
        if (alpha <= 0.01F || size < 2.0F) {
            return;
        }
        float halfWidth = size * 0.866F;
        float halfHeight = size * 0.5F;
        // The cube's top centre sits at (centerX, centerY - size / 2), so the whole cube is centred.
        float topY = centerY - size * 0.5F;
        int topColor = CrystalTheme.fade(0xFFFFFFFF, alpha);
        int leftColor = CrystalTheme.fade(0xFFC8C8C8, alpha);
        int rightColor = CrystalTheme.fade(0xFF9A9A9A, alpha);
        Identifier crack = crackStage >= 0 && crackStage <= 9 ? DESTROY_STAGES[crackStage] : null;
        int crackColor = CrystalTheme.fade(0xD0000000, alpha);
        // Top face: from its left corner, one edge up-right and one down-right.
        face(graphics, top, centerX - halfWidth, topY, halfWidth, -halfHeight, halfWidth, halfHeight, topColor, crack, crackColor);
        // Left face: from the top face's left corner, down-right along the front edge and straight down.
        face(graphics, side, centerX - halfWidth, topY, halfWidth, halfHeight, 0.0F, size, leftColor, crack, crackColor);
        // Right face: from the front corner, up-right and straight down.
        face(graphics, side, centerX, topY + halfHeight, halfWidth, -halfHeight, 0.0F, size, rightColor, crack, crackColor);
    }

    /** One face of a cube: a 16x16 texture mapped onto the parallelogram origin + u*edgeA + v*edgeB. */
    private static void face(GuiGraphicsExtractor graphics, Identifier texture, float originX, float originY, float edgeAX,
            float edgeAY, float edgeBX, float edgeBY, int color, Identifier crack, int crackColor) {
        graphics.pose().pushMatrix();
        graphics.pose().mul(new Matrix3x2f(edgeAX / 16.0F, edgeAY / 16.0F, edgeBX / 16.0F, edgeBY / 16.0F, originX, originY));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, color);
        if (crack != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, crack, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, crackColor);
        }
        graphics.pose().popMatrix();
    }

    /** Text scaled up and centred on {@code centerX}. */
    public static void bigText(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float y, float scale,
            int color, boolean shadow) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX - font.width(text) * scale / 2.0F, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    public static void line(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence line, int x, int y, int color) {
        if (((color >>> 24) & 255) < 8) {
            return;
        }
        graphics.text(font, line, x, y, color, false);
    }

    /** Text cut to {@code width} with an ellipsis. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int room = Math.max(0, width - font.width(ellipsis));
        return font.plainSubstrByWidth(text, room) + ellipsis;
    }

    /**
     * A mouse with its wheel turning: the hint that a region scrolls. Vertical arrows for a list,
     * sideways arrows for the tab carousel. {@code seconds} drives the wheel, {@code alpha} fades it.
     */
    public static void wheelHint(GuiGraphicsExtractor graphics, int centerX, int centerY, double seconds, float alpha,
            boolean sideways, int accent) {
        if (alpha <= 0.02F) {
            return;
        }
        int body = CrystalTheme.fade(0xFFF2E6FA, alpha);
        int shade = CrystalTheme.fade(0xFF2A1238, alpha);
        int glow = CrystalTheme.fade(CrystalTheme.withAlpha(accent, 120), alpha);
        // The mouse: 9 wide, 13 tall, rounded at the top, with a split between the two buttons.
        int left = centerX - 4;
        int top = centerY - 6;
        graphics.fill(left - 1, top + 1, left + 10, top + 13, glow);
        graphics.fill(left + 1, top, left + 8, top + 13, body);
        graphics.fill(left, top + 1, left + 9, top + 12, body);
        graphics.fill(left + 4, top + 1, left + 5, top + 5, shade);
        graphics.fill(left + 1, top + 5, left + 8, top + 6, shade);
        // The wheel rolls down and back, with a short trail.
        double phase = (seconds * 1.6D) % 1.0D;
        int wheelY = top + 1 + (int) Math.round(phase * 3.0D);
        graphics.fill(left + 3, wheelY, left + 6, wheelY + 3, CrystalTheme.fade(accent | 0xFF000000, alpha));
        float bounce = (float) Math.sin(seconds * Math.PI * 3.2D);
        int arrow = CrystalTheme.fade(CrystalTheme.withAlpha(accent, 230), alpha);
        if (sideways) {
            int offset = Math.round(2.0F * bounce);
            arrowLeft(graphics, left - 7 - offset, centerY - 2, arrow);
            arrowRight(graphics, left + 13 + offset, centerY - 2, arrow);
        } else {
            int offset = Math.round(2.0F * bounce);
            arrowUp(graphics, centerX - 2, top - 7 - offset, arrow);
            arrowDown(graphics, centerX - 2, top + 16 + offset, arrow);
        }
    }

    private static void arrowUp(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            graphics.fill(x + 2 - row, y + row, x + 3 + row, y + row + 1, color);
        }
    }

    private static void arrowDown(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            graphics.fill(x + row, y + row, x + 5 - row, y + row + 1, color);
        }
    }

    private static void arrowLeft(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int column = 0; column < 3; column++) {
            graphics.fill(x + column, y + 2 - column, x + column + 1, y + 3 + column, color);
        }
    }

    private static void arrowRight(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int column = 0; column < 3; column++) {
            graphics.fill(x + column, y + column, x + column + 1, y + 5 - column, color);
        }
    }

    /** A chevron pointing left or right, for the carousel's edges. */
    public static void chevron(GuiGraphicsExtractor graphics, int x, int y, boolean right, int color) {
        for (int step = 0; step < 4; step++) {
            int column = right ? x + step : x + 3 - step;
            graphics.fill(column, y + step, column + 1, y + step + 1, color);
            graphics.fill(column, y + 6 - step, column + 1, y + 7 - step, color);
        }
    }
}
