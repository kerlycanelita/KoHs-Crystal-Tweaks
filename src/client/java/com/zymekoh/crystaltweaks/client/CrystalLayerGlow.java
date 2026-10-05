package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The glow: the crystal's three layers shine, each in its own colour, inside a soft aura in the glow
 * colour, and a motion blur can trail them as they turn.
 *
 * <p>Every pass is the crystal's own model drawn again as additive light, so the light keeps the
 * frames' pattern and turns and floats with them. The aura is a few slightly larger copies whose
 * light fades outward; the blur is copies at the angles the layers had a moment ago. Only light is
 * added, on this screen: the crystal, its hitbox and every packet stay as they are.</p>
 *
 * <p>The glow used to be a halo of light behind the crystal; {@link CrystalGlowRenderer} still
 * draws it when Old KoHs Crystal Glow is on.</p>
 */
public final class CrystalLayerGlow {
    static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The aura's copies, as scales of the crystal about its centre, and the share of light each carries. */
    private static final float[] AURA_SCALE = {1.035F, 1.07F, 1.105F, 1.14F};
    private static final float[] AURA_LIGHT = {0.30F, 0.20F, 0.12F, 0.06F};
    /** How far back the blur reaches at 100%, in ticks of the crystal's clock: 24 degrees at Vanilla speed. */
    private static final float BLUR_TICKS = 8.0F;
    private static final int BLUR_STEPS = 8;
    /** All the light the blur adds, at 100% glow power and over, shared by its copies. */
    private static final float BLUR_LIGHT = 0.9F;

    private CrystalLayerGlow() {
    }

    public static void submit(EndCrystalModel model, EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        if (!look.glowActive() || state.distanceToCameraSq > 4096) {
            return;
        }
        RenderType light = CrystalGlowMaterial.layers(TEXTURE);
        float power = CrystalGlowMath.power(look.glowPowerPercent);
        // Past 32 blocks a crystal is a few dozen pixels wide: neither the aura nor the blur would show.
        boolean near = state.distanceToCameraSq < 1024;
        if (!CrystalVisualConfig.oldGlow()) {
            CrystalLayerGlowState layers = pass(state, look, state.ageInTicks, true);
            // A pass adds at most its full colour; above 100% the light takes more passes.
            int passes = Math.max(1, (int) Math.ceil(power));
            for (int pass = 0; pass < passes; pass++) {
                draw(model, layers, look, poses, collector, light, 1.0F, power / passes);
            }
            if (near) {
                CrystalLayerGlowState aura = pass(state, look, state.ageInTicks, false);
                float auraPower = Math.min(1.5F, power);
                for (int copy = 0; copy < AURA_SCALE.length; copy++) {
                    draw(model, aura, look, poses, collector, light, AURA_SCALE[copy], AURA_LIGHT[copy] * auraPower);
                }
            }
        }
        if (near && look.motionBlurPercent > 0 && (look.rotationSpeedPercent > 0 || look.floatingSpeedPercent > 0)) {
            float span = BLUR_TICKS * look.motionBlurPercent / 100.0F;
            // A trail that fades as it goes back, sharing a fixed amount of light: added light only
            // brightens, and a copy laid almost over the crystal would burn it white rather than blur it.
            float shared = BLUR_LIGHT * Math.min(1.0F, power) / (BLUR_STEPS / 2.0F);
            for (int step = 1; step <= BLUR_STEPS; step++) {
                float fade = 1.0F - (step - 0.5F) / BLUR_STEPS;
                draw(model, pass(state, look, state.ageInTicks - span * step / BLUR_STEPS, true), look, poses,
                        collector, light, 1.0F, shared * fade);
            }
        }
    }

    /** A pass's own copy of the state: the model is posed from it when the pass is drawn, not now. */
    private static CrystalLayerGlowState pass(EndCrystalRenderState source, CrystalAppearance look, float age,
            boolean ownColours) {
        CrystalLayerGlowState pass = new CrystalLayerGlowState(ownColours);
        pass.ageInTicks = age;
        // The bedrock base never shines.
        pass.showsBottom = false;
        pass.lightCoords = source.lightCoords;
        ((CrystalAppearanceAccess) (Object) pass).crystalTweaks$appearance(look);
        return pass;
    }

    /**
     * @param scale how much larger than the crystal, about the layers' shared centre
     * @param gain  how much light the pass adds: 1 adds each layer's full colour
     */
    private static void draw(EndCrystalModel model, CrystalLayerGlowState pass, CrystalAppearance look,
            PoseStack poses, SubmitNodeCollector collector, RenderType light, float scale, float gain) {
        if (gain <= 0.004F) {
            return;
        }
        // The pass's light travels in the tint's alpha; CrystalLayerTint turns it into each layer's colour.
        int glowTint = CrystalGlowMath.alpha(gain) << 24 | 0xFFFFFF;
        poses.pushPose();
        if (scale != 1.0F) {
            // The layers float, and the aura grows around them wherever they are.
            float centre = 2.0F + EndCrystalRenderer.getY(pass.ageInTicks * look.floatingSpeedPercent / 100.0F);
            poses.translate(0.0F, centre, 0.0F);
            poses.scale(scale, scale, scale);
            poses.translate(0.0F, -centre, 0.0F);
        }
        // Vanilla's own placement of the model.
        poses.scale(2.0F, 2.0F, 2.0F);
        poses.translate(0.0F, -0.5F, 0.0F);
        collector.submitModel(model, pass, poses, light, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowTint, null, 0, null);
        poses.popPose();
    }
}
