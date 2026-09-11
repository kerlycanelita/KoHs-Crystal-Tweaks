package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

    private static final String[] STEVE = {
            "##########",
            "##########",
            "##########",
            "##########",
            "#..####..#",
            "#..####..#",
            "####..####",
            "##########",
            "##......##",
            "##########",
    };

    /** Bolts thrown per flash. Past this the screen reads as noise rather than as lightning. */
    private static final int MAX_BOLTS = 5;

    /** Players further than this contribute no bolt. */
    private static final double BOLT_RANGE = 24.0D;

    private CrystalFlashShapes() { }

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
            case STEVE -> mask(buffer, matrix, STEVE, color, hotColor, radius * 1.05F, gain);
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
        float previousX = 0F;
        float previousY = 0F;
        for (int segment = 1; segment <= segments; segment++) {
            float travel = length * segment / segments;
            float sway = segment == segments ? 0F : (noise(seed + segment) - 0.5F) * length * 0.22F;
            float x = dirX * travel + normalX * sway;
            float y = dirY * travel + normalY * sway;
            float taper = 1F - (segment - 1F) / segments;
            float width = length * 0.045F * taper;
            ribbon(buffer, matrix, color, previousX, previousY, x, y, width, gain * 0.55F * taper);
            ribbon(buffer, matrix, hotColor, previousX, previousY, x, y, width * 0.38F, gain * taper);
            previousX = x;
            previousY = y;
        }
    }

    private static void ribbon(
            VertexConsumer buffer,
            Matrix4f matrix,
            int color,
            float x0,
            float y0,
            float x1,
            float y1,
            float width,
            float gain
    ) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001F) {
            return;
        }
        float nx = -dy / length * width;
        float ny = dx / length * width;
        triangle(buffer, matrix, color,
                x0 + nx, y0 + ny, gain, x0 - nx, y0 - ny, gain, x1 - nx, y1 - ny, gain);
        triangle(buffer, matrix, color,
                x0 + nx, y0 + ny, gain, x1 - nx, y1 - ny, gain, x1 + nx, y1 + ny, gain);
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
