package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Behind the crystal in the settings: a castle's curtain wall under a dark violet sky, its merlons
 * against the stars, and an obsidian floor in front for the crystal to float over. Every block is as
 * large as a block of the crystal's: the wall is the ruler its real size is read against.
 *
 * <p>Drawn flat, seen with the crystal's own slight tilt, so the tops of the floor and of the coping
 * show a little. The wall is one repeating texture of the mod's own stone and the floor the game's
 * obsidian, also repeated: a few dozen elements whatever the size of the page.</p>
 */
final class CurtainWall {
    /** How far the crystal is tipped towards the viewer, in radians: the floor's top shows by its sine. */
    static final float TILT = 0.18F;
    static final float SIN_TILT = (float) Math.sin(TILT);
    static final float COS_TILT = (float) Math.cos(TILT);
    /** How deep the floor is from front to back, in blocks: the crystal stands in its middle. */
    static final float DEPTH = 3.0F;
    /** How much of the floor's front shows under its near edge, in blocks. */
    static final float FRONT = 0.45F;
    /** How far below the crystal's feet the page's bottom is, in blocks. */
    static final float BELOW = DEPTH * SIN_TILT / 2.0F + FRONT;
    /** Blocks of wall from the floor to the foot of the merlons. */
    private static final int COURSES = 3;
    private static final int SKY_TOP = 0xFF07040B;
    private static final int SKY_LOW = 0xFF1A1026;
    private static final int WALL_TINT = 0xFFE6DEEE;
    private static final int COPING_TINT = 0xFFF4EEFA;
    private static final int FLOOR_TOP_TINT = 0xFF968CAC;
    private static final int FLOOR_FRONT_TINT = 0xFF7A7090;
    /** The page the wall fades into at its sides. */
    private static final int EDGE = 0xFF0C0811;
    private static final int STARS = 16;
    private static final int EDGE_STRIPS = 8;

    private CurtainWall() {
    }

    /**
     * @param area    the page's stage, which the scene fills
     * @param centerX the crystal's middle, across
     * @param feetY   the crystal's feet: the middle of the floor's top
     * @param zoom    pixels to a block
     * @param alpha   how much of the scene shows, for the book's entrance
     */
    static void draw(GuiGraphicsExtractor graphics, Rect area, float centerX, float feetY, float zoom, float size, CrystalAppearance look,
            double seconds, float alpha) {
        if (area.isEmpty() || zoom < 2.0F || alpha <= 0.02F) {
            return;
        }
        float farY = Math.round(feetY - DEPTH * SIN_TILT * zoom / 2.0F);
        float nearY = Math.round(feetY + DEPTH * SIN_TILT * zoom / 2.0F);
        // The block the crystal stands on is centred under it; every course lines up with it.
        float columnX = Math.round(centerX - zoom / 2.0F);
        float wallTop = farY - COURSES * zoom;
        float coping = Math.max(1.0F, SIN_TILT * zoom);
        int left = area.x();
        int right = area.right();
        int top = area.y();
        int bottom = area.bottom();

        // The sky, darkest overhead, with its stars.
        graphics.fillGradient(left, top, right, Math.round(farY), CrystalTheme.fade(SKY_TOP, alpha), CrystalTheme.fade(SKY_LOW, alpha));
        drawStars(graphics, left, top, right - left, Math.round(Math.min(farY, wallTop) - top), seconds, alpha);

        // The wall, its coping, and a merlon on every other block, the crystal's own a gap.
        Identifier wall = GrimoireIcons.wall();
        Identifier cap = GrimoireIcons.coping();
        tiled(graphics, wall, GrimoireIcons.WALL_WIDTH, GrimoireIcons.WALL_HEIGHT, left, wallTop, right, farY, columnX, farY, zoom, zoom,
                CrystalTheme.fade(WALL_TINT, alpha));
        tiled(graphics, cap, 16, 16, left, wallTop - coping, right, wallTop, columnX, wallTop - coping, zoom, coping,
                CrystalTheme.fade(COPING_TINT, alpha));
        int first = (int) Math.floor((left - columnX) / zoom);
        int last = (int) Math.ceil((right - columnX) / zoom);
        for (int column = first; column <= last; column++) {
            if ((column & 1) == 0) {
                continue;
            }
            float x0 = Math.max(left, columnX + column * zoom);
            float x1 = Math.min(right, columnX + (column + 1) * zoom);
            if (x1 <= x0) {
                continue;
            }
            tiled(graphics, wall, GrimoireIcons.WALL_WIDTH, GrimoireIcons.WALL_HEIGHT, x0, wallTop - zoom, x1, wallTop, columnX, farY, zoom, zoom,
                    CrystalTheme.fade(WALL_TINT, alpha));
            tiled(graphics, cap, 16, 16, x0, wallTop - zoom - coping, x1, wallTop - zoom, columnX, wallTop - zoom - coping, zoom, coping,
                    CrystalTheme.fade(COPING_TINT, alpha));
        }
        // Shadow where the wall meets the floor, and a slow mist along its foot.
        HubDraw.isolate(graphics);
        int shadowTop = Math.round(farY - 0.35F * zoom);
        graphics.fillGradient(left, shadowTop, right, Math.round(farY), 0x00000000, CrystalTheme.fade(0x8C000000, alpha));
        float drift = 0.5F + 0.5F * (float) Math.sin(seconds * 0.4D);
        int mist = CrystalTheme.withAlpha(look.haloColor(), Math.round((10 + 12 * drift) * alpha));
        graphics.fillGradient(left, Math.round(farY - 0.9F * zoom), right, Math.round(farY), mist & 0x00FFFFFF, mist);

        // The floor: its top seen at the tilt, one polished block deep (squeezed any further, its
        // stone would break into stripes), then its front, darker.
        HubDraw.isolate(graphics);
        Identifier floor = GrimoireIcons.floor();
        tiled(graphics, floor, 16, 16, left, farY, right, nearY, columnX, farY, zoom, nearY - farY, CrystalTheme.fade(FLOOR_TOP_TINT, alpha));
        tiled(graphics, floor, 16, 16, left, nearY, right, bottom, columnX, nearY, zoom, COS_TILT * zoom, CrystalTheme.fade(FLOOR_FRONT_TINT, alpha));
        graphics.fill(left, Math.round(nearY), right, Math.round(nearY) + 1, CrystalTheme.fade(0x40F4EEFA, alpha));
        graphics.fillGradient(left, Math.round(nearY) + 1, right, bottom, 0x00000000, CrystalTheme.fade(0xA0000000, alpha));

        // The crystal's light on the wall and the floor, as its glow and reflections would cast it.
        HubDraw.isolate(graphics);
        boolean lit = look.glowActive();
        int colour = lit ? look.haloColor() : 0xFF8A7CA8;
        float power = lit ? Math.min(1.0F, CrystalGlowMath.power(look.glowPowerPercent)) : 0.0F;
        float centreY = feetY - COS_TILT * zoom;
        int reach = Math.round((1.4F + 0.6F * size) * zoom);
        HubDraw.glowDisc(graphics, Math.round(centerX), Math.round(centreY), reach, colour, alpha * (lit ? 0.16F + 0.24F * power : 0.08F));
        float reflections = lit ? Math.min(1.5F, look.glowReflectionsPercent / 100.0F) : 0.0F;
        float pool = alpha * (lit ? 0.08F + 0.2F * reflections * Math.max(0.3F, power) : 0.04F);
        HubDraw.texture(graphics, GrimoireIcons.glow(), GrimoireIcons.GLOW_SIZE, GrimoireIcons.GLOW_SIZE, centerX, feetY, reach * 2.2F,
                Math.max(4.0F, (nearY - farY) * 1.6F), 0.0F, Math.round(255.0F * Math.min(1.0F, pool)) << 24 | (colour & 0xFFFFFF));

        // The sides fade into the page, so the scene sits in the book rather than on it.
        HubDraw.isolate(graphics);
        int fade = Math.max(4, (right - left) / 5);
        for (int strip = 0; strip < EDGE_STRIPS; strip++) {
            int from = fade * strip / EDGE_STRIPS;
            int to = fade * (strip + 1) / EDGE_STRIPS;
            float weight = 1.0F - (strip + 0.5F) / EDGE_STRIPS;
            int shade = CrystalTheme.withAlpha(EDGE, Math.round(200 * weight * weight * alpha));
            graphics.fill(left + from, top, left + to, bottom, shade);
            graphics.fill(right - to, top, right - from, bottom, shade);
        }
        HubDraw.isolate(graphics);
    }

    /** A few faint stars, each in its own place and twinkling on its own beat. */
    private static void drawStars(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double seconds, float alpha) {
        if (width < 8 || height < 6) {
            return;
        }
        for (int star = 0; star < STARS; star++) {
            // A scattering that never moves: the golden ratio across, a second step down.
            float across = (star * 0.618034F + 0.13F) % 1.0F;
            float down = (star * 0.414214F + 0.31F) % 1.0F;
            int sx = x + Math.round(across * (width - 2));
            int sy = y + Math.round(down * down * (height - 2));
            float twinkle = 0.45F + 0.55F * (float) Math.abs(Math.sin(seconds * (0.6D + star % 5 * 0.17D) + star * 1.7D));
            int size = star % 6 == 0 ? 2 : 1;
            graphics.fill(sx, sy, sx + size, sy + size, CrystalTheme.withAlpha(star % 4 == 0 ? 0xE9D5FF : 0xBFB2CF,
                    Math.round(150 * twinkle * alpha)));
        }
    }

    /**
     * Fills (x0, y0)-(x1, y1) with a repeating texture whose 16-texel blocks are {@code blockX} by
     * {@code blockY} pixels, laid from (originX, originY): one element however many blocks it holds.
     */
    private static void tiled(GuiGraphicsExtractor graphics, Identifier texture, int textureWidth, int textureHeight, float x0, float y0, float x1,
            float y1, float originX, float originY, float blockX, float blockY, int colour) {
        if (x1 - x0 < 0.5F || y1 - y0 < 0.5F || blockX < 0.5F || blockY < 0.1F || ((colour >>> 24) & 255) < 4) {
            return;
        }
        float texelX = blockX / 16.0F;
        float texelY = blockY / 16.0F;
        int u0 = (int) Math.floor((x0 - originX) / texelX);
        int u1 = (int) Math.ceil((x1 - originX) / texelX);
        int v0 = (int) Math.floor((y0 - originY) / texelY);
        int v1 = (int) Math.ceil((y1 - originY) / texelY);
        graphics.enableScissor(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1));
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(texelX, texelY);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, u0, v0, u0, v0, u1 - u0, v1 - v0, textureWidth, textureHeight, colour);
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }
}
