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

    /**
     * @param centerHeight height of the billboard's centre above the crystal's base, in blocks
     * @param upX          height gained per unit along the billboard's own x axis
     * @param upY          height gained per unit along the billboard's own y axis
     */
    CrystalGlowBuffer(VertexConsumer buffer, float centerHeight, float upX, float upY) {
        this.buffer = buffer;
        this.centerHeight = centerHeight;
        this.upX = upX;
        this.upY = upY;
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
