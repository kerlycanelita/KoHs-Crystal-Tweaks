package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Where glow geometry goes, with its light faded out toward the ground.
 *
 * <p>Every halo, flash and head is a flat billboard facing the camera. A large one reaches below
 * the crystal, into the block it stands on, and the depth test cut it off there along a hard
 * straight line that slid around as the camera moved. Each vertex now loses its light over the
 * last stretch above the crystal's base, so the billboard meets the ground already dark.</p>
 */
final class CrystalGlowBuffer {
    /** Height above the crystal's base, in blocks, from which a vertex keeps all of its light. */
    static final float GROUND_FADE = 0.7F;

    private final VertexConsumer buffer;
    private final float centerHeight;
    private final float upX;
    private final float upY;
    private final boolean doubleSided;

    /**
     * @param centerHeight height of the billboard's centre above the crystal's base, in blocks
     * @param upX          height gained per unit along the billboard's own x axis
     * @param upY          height gained per unit along the billboard's own y axis
     * @param doubleSided  emit every triangle in both windings, for a pose that may be mirrored
     */
    CrystalGlowBuffer(VertexConsumer buffer, float centerHeight, float upX, float upY, boolean doubleSided) {
        this.buffer = buffer;
        this.centerHeight = centerHeight;
        this.upX = upX;
        this.upY = upY;
        this.doubleSided = doubleSided;
    }

    /**
     * One triangle of the billboard, as a single face or as both.
     *
     * <p>The material culls back faces. In the world the billboard is turned to face the camera, so
     * its front is the side wound counter-clockwise in billboard space, and that is the only one
     * emitted: the other was culled on every frame and only cost vertices, half of all the glow
     * sent. The settings preview draws through a mirrored pose, and there both windings go out.</p>
     */
    void triangle(Matrix4f matrix, int color, float ax, float ay, float aa, float bx, float by, float ba,
            float cx, float cy, float ca) {
        if (this.doubleSided) {
            vertex(matrix, color, ax, ay, aa); vertex(matrix, color, bx, by, ba); vertex(matrix, color, cx, cy, ca);
            vertex(matrix, color, cx, cy, ca); vertex(matrix, color, bx, by, ba); vertex(matrix, color, ax, ay, aa);
            return;
        }
        vertex(matrix, color, ax, ay, aa);
        if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) >= 0F) {
            vertex(matrix, color, bx, by, ba); vertex(matrix, color, cx, cy, ca);
        } else {
            vertex(matrix, color, cx, cy, ca); vertex(matrix, color, bx, by, ba);
        }
    }

    /** How much light a vertex at billboard position (x, y) keeps, from 0 at the base to 1. */
    float groundFade(float x, float y) {
        float t = (this.centerHeight + x * this.upX + y * this.upY) / GROUND_FADE;
        if (t >= 1F) {
            return 1F;
        }
        if (t <= 0F) {
            return 0F;
        }
        return t * t * (3F - 2F * t);
    }

    /** A vertex of the additive light material. */
    void vertex(Matrix4f matrix, int color, float x, float y, float alpha) {
        this.buffer.addVertex(matrix, x, y, 0F)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255,
                        CrystalGlowMath.alpha(alpha * groundFade(x, y)));
    }

    /** A vertex of a textured, translucent billboard such as the player's head. */
    void texturedVertex(Matrix4f matrix, int r, int g, int b, int alpha, float x, float y, float u, float v,
            int overlay, int light) {
        this.buffer.addVertex(matrix, x, y, 0F)
                .setColor(r, g, b, Math.round(alpha * groundFade(x, y)))
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0F, 0F, 1F);
    }
}
