package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Silhouettes for the death flash, drawn in the same camera-facing plane and the same additive
 * material as the original burst.
 *
 * <p>Everything here is geometry submitted for one frame. No entity is read for collision, no
 * position is reported anywhere, and the lightning style only looks at players the client is already
 * rendering, to point the bolts somewhere. It never selects, tracks or reveals a target: a player
 * behind a wall throws no bolt worth following, because the bolt stops at the blast's own radius.</p>
 */
public final class CrystalFlashShapes {
    /** Mask rows are drawn top to bottom; '#' is lit, '.' is a hole the additive pass skips. */
    private static final String[] SKULL = {
            "....######....",
            "..##########..",
            ".############.",
            "##############",
            "##############",
            "##...####...##",
            "#....####....#",
            "##...####...##",
            "##############",
            "######..######",
            ".#####..#####.",
            "..##########..",
            "...##.##.##...",
    };

    /** Bolts thrown per flash. Past this the screen reads as noise rather than as lightning. */
    private static final int MAX_BOLTS = 5;

    /** Players further than this contribute no bolt. */
    private static final double BOLT_RANGE = 24.0D;

    /** Full block light, so the apparition is as bright in a cave as it is at noon. */
    private static final int FULL_BRIGHT = 15728880;

    /** No damage overlay on the head. */
    private static final int NO_OVERLAY = 655360;

    private CrystalFlashShapes() { }

    /** Texture of the local player's own skin, or {@code null} before one is available. */
    public static Identifier playerSkin() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        try {
            return ((AbstractClientPlayer) player).getSkin().body().texturePath();
        } catch (RuntimeException skinNotReady) {
            return null;
        }
    }

    /**
     * Draws the player's own head as an apparition: the face from their skin, tinted with the chosen
     * glow colour and fading with the flash, plus the hat layer a little larger in front of it.
     *
     * <p>The skin is the one the client is already rendering on the player. Nothing is downloaded,
     * and no other player's skin is read.</p>
     */
    public static void submitPlayerHead(
            VertexConsumer buffer, Matrix4f matrix, int color, float radius, float gain) {
        float half = radius * 0.95F;
        // A skin is 64 wide: the face sits at x 8-16, y 8-16, and the hat layer at x 40-48.
        face(buffer, matrix, color, half, gain, 0.125F, 0.125F, 0.25F, 0.25F);
        face(buffer, matrix, color, half * 1.12F, gain * 0.55F, 0.625F, 0.125F, 0.75F, 0.25F);
    }

    private static void face(VertexConsumer buffer, Matrix4f matrix, int color, float half,
            float gain, float u0, float v0, float u1, float v1) {
        int alpha = CrystalGlowMath.alpha(gain);
        if (alpha <= 0) {
            return;
        }
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        // Two windings so the settings screen's mirrored pose shows the head too.
        for (int pass = 0; pass < 2; pass++) {
            boolean flipped = pass == 1;
            headVertex(buffer, matrix, r, g, b, alpha, -half, -half, flipped ? u1 : u0, v1);
            headVertex(buffer, matrix, r, g, b, alpha, half, -half, flipped ? u0 : u1, v1);
            headVertex(buffer, matrix, r, g, b, alpha, half, half, flipped ? u0 : u1, v0);
            headVertex(buffer, matrix, r, g, b, alpha, -half, half, flipped ? u1 : u0, v0);
        }
    }

    private static void headVertex(VertexConsumer buffer, Matrix4f matrix,
            int r, int g, int b, int alpha, float x, float y, float u, float v) {
        buffer.addVertex(matrix, x, y, 0F)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0F, 0F, 1F);
    }

    /**
     * @param origin world position of the blast, or {@code null} for the settings-screen preview
     *               where no level exists to look for players in
     */
    public static void submit(
            CrystalFlashStyle style,
            VertexConsumer buffer,
            Matrix4f matrix,
            Quaternionf cameraOrientation,
            int color,
            int hotColor,
            float radius,
            float gain,
            Vec3 origin
    ) {
        switch (style) {
            case SKULL -> mask(buffer, matrix, SKULL, color, hotColor, radius * 1.15F, gain);
            case LIGHTNING -> bolts(buffer, matrix, cameraOrientation, color, hotColor, radius, gain, origin);
            default -> { }
        }
    }

    /**
     * Draws a mask as one quad per lit cell, brightest at the middle so the shape still reads as a
     * burst of light rather than as a flat sticker pasted over the world.
     */
    private static void mask(
            VertexConsumer buffer,
            Matrix4f matrix,
            String[] rows,
            int color,
            int hotColor,
            float radius,
            float gain
    ) {
        int height = rows.length;
        int width = rows[0].length();
        float cell = radius * 2F / Math.max(width, height);
        float originX = -cell * width / 2F;
        float originY = cell * height / 2F;
        for (int row = 0; row < height; row++) {
            String line = rows[row];
            for (int column = 0; column < width && column < line.length(); column++) {
                if (line.charAt(column) != '#') {
                    continue;
                }
                float x0 = originX + cell * column;
                float y0 = originY - cell * (row + 1);
                // Cells near the middle burn hotter, which keeps the silhouette from looking flat.
                float dx = (column + 0.5F) / width - 0.5F;
                float dy = (row + 0.5F) / height - 0.5F;
                float centre = Math.max(0F, 1F - (float) Math.sqrt(dx * dx + dy * dy) * 1.7F);
                quad(buffer, matrix, color, x0, y0, x0 + cell, y0 + cell, gain * (0.55F + centre * 0.45F));
                if (centre > 0.62F) {
                    quad(buffer, matrix, hotColor,
                            x0 + cell * 0.18F, y0 + cell * 0.18F,
                            x0 + cell * 0.82F, y0 + cell * 0.82F,
                            gain * (centre - 0.62F) * 1.4F);
                }
            }
        }
    }

    /**
     * Throws a jagged bolt toward every nearby player, projected into the camera-facing plane so the
     * bolt reads from any angle.
     */
    private static void bolts(
            VertexConsumer buffer,
            Matrix4f matrix,
            Quaternionf cameraOrientation,
            int color,
            int hotColor,
            float radius,
            float gain,
            Vec3 origin
    ) {
        List<float[]> directions = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (origin != null && minecraft.level != null && cameraOrientation != null) {
            LocalPlayer self = minecraft.player;
            Quaternionf inverse = new Quaternionf(cameraOrientation).conjugate();
            for (Player player : minecraft.level.players()) {
                if (player == self || directions.size() >= MAX_BOLTS) {
                    continue;
                }
                Vec3 delta = player.position().add(0, player.getBbHeight() / 2, 0).subtract(origin);
                double distance = delta.length();
                if (distance < 0.5D || distance > BOLT_RANGE) {
                    continue;
                }
                Vector3f local = inverse.transform(
                        new Vector3f((float) delta.x, (float) delta.y, (float) delta.z));
                float planar = (float) Math.sqrt(local.x * local.x + local.y * local.y);
                if (planar < 0.0001F) {
                    continue;
                }
                // Reach is the blast's own, not the player's: a distant enemy gets a longer-looking
                // bolt, never a line that measures out to them.
                float reach = radius * (1.15F + (float) Math.min(1.0D, distance / BOLT_RANGE) * 0.85F);
                directions.add(new float[] {local.x / planar, local.y / planar, reach});
            }
        }
        if (directions.isEmpty()) {
            // No one around, or the preview: a ring of bolts so the style is never invisible.
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2 * i / 6;
                directions.add(new float[] {
                        (float) Math.cos(angle), (float) Math.sin(angle), radius * 1.4F});
            }
        }

        for (int index = 0; index < directions.size(); index++) {
            float[] bolt = directions.get(index);
            // Deterministic per bolt, so a bolt does not reshuffle its own kinks between frames.
            long seed = index * 2654435761L;
            jagged(buffer, matrix, color, hotColor, bolt[0], bolt[1], bolt[2], gain, seed);
        }
        // A small core keeps the bolts anchored to something instead of starting in empty air.
        disc(buffer, matrix, hotColor, radius * 0.22F, gain * 0.9F);
    }

    /**
     * Draws one bolt as a single continuous strip.
     *
     * <p>Each segment used to be its own quad with its own normal, so at every kink the two quads
     * met at different angles and left a notch: the bolt read as a row of separate dashes rather
     * than one line. The joints now share their vertices, and the normal at a joint is the average
     * of the two segments meeting there, which is what closes the gap.</p>
     */
    private static void jagged(
            VertexConsumer buffer,
            Matrix4f matrix,
            int color,
            int hotColor,
            float dirX,
            float dirY,
            float length,
            float gain,
            long seed
    ) {
        int segments = 6;
        float normalX = -dirY;
        float normalY = dirX;
        float[] xs = new float[segments + 1];
        float[] ys = new float[segments + 1];
        for (int point = 0; point <= segments; point++) {
            float travel = length * point / segments;
            float sway = (point == 0 || point == segments)
                    ? 0F
                    : (noise(seed + point) - 0.5F) * length * 0.22F;
            xs[point] = dirX * travel + normalX * sway;
            ys[point] = dirY * travel + normalY * sway;
        }
        strip(buffer, matrix, color, xs, ys, length * 0.045F, gain * 0.55F);
        strip(buffer, matrix, hotColor, xs, ys, length * 0.017F, gain);
    }

    /** Emits a tapering strip through the points, mitring the normal at every joint. */
    private static void strip(
            VertexConsumer buffer,
            Matrix4f matrix,
            int color,
            float[] xs,
            float[] ys,
            float width,
            float gain
    ) {
        int points = xs.length;
        float[] offsetX = new float[points];
        float[] offsetY = new float[points];
        for (int point = 0; point < points; point++) {
            float inX = point > 0 ? xs[point] - xs[point - 1] : xs[1] - xs[0];
            float inY = point > 0 ? ys[point] - ys[point - 1] : ys[1] - ys[0];
            float outX = point < points - 1 ? xs[point + 1] - xs[point] : inX;
            float outY = point < points - 1 ? ys[point + 1] - ys[point] : inY;
            float nx = normalize(-inY, inX, 0) + normalize(-outY, outX, 0);
            float ny = normalize(-inY, inX, 1) + normalize(-outY, outX, 1);
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 0.0001F) {
                nx = -inY;
                ny = inX;
                len = (float) Math.sqrt(nx * nx + ny * ny);
                if (len < 0.0001F) {
                    len = 1F;
                }
            }
            // The bolt narrows toward its tip and fades out with it.
            float taper = 1F - (float) point / points * 0.75F;
            float half = width * taper / len;
            offsetX[point] = nx * half;
            offsetY[point] = ny * half;
        }
        for (int segment = 0; segment < points - 1; segment++) {
            float a = gain * (1F - (float) segment / points * 0.8F);
            float b = gain * (1F - (float) (segment + 1) / points * 0.8F);
            float x0 = xs[segment], y0 = ys[segment], x1 = xs[segment + 1], y1 = ys[segment + 1];
            triangle(buffer, matrix, color,
                    x0 + offsetX[segment], y0 + offsetY[segment], a,
                    x0 - offsetX[segment], y0 - offsetY[segment], a,
                    x1 - offsetX[segment + 1], y1 - offsetY[segment + 1], b);
            triangle(buffer, matrix, color,
                    x0 + offsetX[segment], y0 + offsetY[segment], a,
                    x1 - offsetX[segment + 1], y1 - offsetY[segment + 1], b,
                    x1 + offsetX[segment + 1], y1 + offsetY[segment + 1], b);
        }
    }

    /** Component {@code axis} of the unit vector along (x, y); 0 for a degenerate segment. */
    private static float normalize(float x, float y, int axis) {
        float length = (float) Math.sqrt(x * x + y * y);
        if (length < 0.0001F) {
            return 0F;
        }
        return (axis == 0 ? x : y) / length;
    }

    private static void disc(VertexConsumer buffer, Matrix4f matrix, int color, float radius, float gain) {
        int segments = 20;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2 * segment / segments;
            double a1 = Math.PI * 2 * (segment + 1) / segments;
            triangle(buffer, matrix, color,
                    0F, 0F, gain,
                    (float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, 0F,
                    (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0F);
        }
    }

    private static void quad(
            VertexConsumer buffer,
            Matrix4f matrix,
            int color,
            float x0,
            float y0,
            float x1,
            float y1,
            float gain
    ) {
        triangle(buffer, matrix, color, x0, y0, gain, x1, y0, gain, x1, y1, gain);
        triangle(buffer, matrix, color, x0, y0, gain, x1, y1, gain, x0, y1, gain);
    }

    /** Cheap deterministic hash in [0,1); no allocation and no shared Random between frames. */
    private static float noise(long seed) {
        long value = seed * 6364136223846793005L + 1442695040888963407L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return (value >>> 40) / (float) (1 << 24);
    }

    private static void triangle(
            VertexConsumer buffer,
            Matrix4f matrix,
            int color,
            float ax, float ay, float aa,
            float bx, float by, float ba,
            float cx, float cy, float ca
    ) {
        vertex(buffer, matrix, color, ax, ay, aa);
        vertex(buffer, matrix, color, bx, by, ba);
        vertex(buffer, matrix, color, cx, cy, ca);
        // Opposite winding supports the settings screen's mirrored pose, as the burst already does.
        vertex(buffer, matrix, color, cx, cy, ca);
        vertex(buffer, matrix, color, bx, by, ba);
        vertex(buffer, matrix, color, ax, ay, aa);
    }

    private static void vertex(
            VertexConsumer buffer, Matrix4f matrix, int color, float x, float y, float alpha) {
        buffer.addVertex(matrix, x, y, 0F)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, CrystalGlowMath.alpha(alpha));
    }
}
