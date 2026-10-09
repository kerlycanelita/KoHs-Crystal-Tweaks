package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Where the crystal's three boxes are at a moment of its clock, with the player's rotation speed,
 * floating speed and size: one description, for the model that is drawn and for the light drawn
 * round it.
 */
public final class CrystalPose {
    private static final float SINE_45 = (float) Math.sin(Math.PI / 4.0D);
    private static final float TILT = (float) (Math.PI / 3.0D);
    /**
     * Blocks above the crystal's feet its hitbox is centred on. The layers float round this height
     * and grow or shrink about it, so a crystal of any size stays in the middle of what can be hit.
     */
    private static final float MIDDLE = 1.0F;

    private CrystalPose() {
    }

    public static float size(CrystalAppearance look) {
        return look.sizePercent / 100.0F;
    }

    /** Whether the model needs posing here rather than by Vanilla: any speed or size that is not Vanilla's. */
    public static boolean custom(CrystalAppearance look) {
        return look.rotationSpeedPercent != 100 || look.floatingSpeedPercent != 100 || look.sizePercent != 100;
    }

    /** How far above the crystal's feet the layers' shared centre is, in blocks. */
    public static float centreHeight(float ageInTicks, CrystalAppearance look) {
        return MIDDLE + size(look) * (1.0F + EndCrystalRenderer.getY(ageInTicks * look.floatingSpeedPercent / 100.0F));
    }

    /**
     * The same centre in the model's own units, sixteenths of a block before the renderer doubles
     * the model and lowers it half a block.
     */
    public static float modelCentre(float ageInTicks, CrystalAppearance look) {
        return 8.0F * (centreHeight(ageInTicks, look) + 1.0F);
    }

    /** The outer frame's turn: about the vertical, standing on a corner. */
    public static Quaternionf outerTurn(float ageInTicks, CrystalAppearance look) {
        return new Quaternionf().rotationY(turn(ageInTicks, look)).rotateAxis(TILT, SINE_45, 0.0F, SINE_45);
    }

    /** The inner frame's turn inside the outer one, and the core's inside the inner frame. */
    public static Quaternionf nestedTurn(float ageInTicks, CrystalAppearance look) {
        return new Quaternionf().setAngleAxis(TILT, SINE_45, 0.0F, SINE_45).rotateY(turn(ageInTicks, look));
    }

    /** How far the crystal has turned about its own axis, in radians. */
    static float turn(float ageInTicks, CrystalAppearance look) {
        return ageInTicks * look.rotationSpeedPercent / 100.0F * 3.0F * (float) (Math.PI / 180.0D);
    }

    /**
     * The three boxes from the crystal's feet, in blocks: the renderer's placement and the model's
     * pose in one step. A box's own coordinates run a quarter of a unit each way.
     *
     * @param innerScale how much smaller the inner frame is than the outer one
     * @param coreScale  how much smaller the core is than the inner frame
     */
    public static void matrices(float ageInTicks, CrystalAppearance look, float innerScale, float coreScale,
            Matrix4f outer, Matrix4f inner, Matrix4f core) {
        Quaternionf nested = nestedTurn(ageInTicks, look);
        outer.translation(0.0F, centreHeight(ageInTicks, look), 0.0F).rotate(outerTurn(ageInTicks, look))
                .scale(2.0F * size(look));
        inner.set(outer).rotate(nested).scale(innerScale);
        core.set(inner).rotate(nested).scale(coreScale);
    }
}
