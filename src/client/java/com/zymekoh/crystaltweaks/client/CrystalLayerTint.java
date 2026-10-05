package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;

/**
 * Applies per-part crystal colors during the single model pass used by vanilla.
 * Rendering happens on Minecraft's render thread, so no cross-thread state is needed.
 */
public final class CrystalLayerTint {
    private static EndCrystalModel activeModel;

    private CrystalLayerTint() {
    }

    public static void begin(EndCrystalModel model) {
        activeModel = model;
    }

    public static void end(EndCrystalModel model) {
        if (activeModel == model) {
            activeModel = null;
        }
    }

    public static int colorFor(ModelPart part, int originalColor) {
        EndCrystalModel model = activeModel;
        if (model == null) {
            return originalColor;
        }
        CrystalAppearance appearance = CrystalAppearanceAccess.of(model);
        CrystalLayerGlowState glow = ((CrystalAppearanceAccess) (Object) model).crystalTweaks$glowPass();
        if (glow != null) {
            // A pass of the glow: light, whose share arrives in the tint's alpha.
            return light(glow.ownColours ? shineColour(part, model, appearance) : appearance.haloColor(),
                    (originalColor >>> 24) / 255.0F);
        }
        if (part == model.cube && appearance.glowPowerPercent > 0) {
            int tint = appearance.coreColor;
            int hot = CrystalGlowMath.hotColor(tint);
            float amount = Math.min(1, CrystalGlowMath.power(appearance.glowPowerPercent));
            int r = Math.round(((tint >> 16) & 255) * (1 - amount) + ((hot >> 16) & 255) * amount);
            int g = Math.round(((tint >> 8) & 255) * (1 - amount) + ((hot >> 8) & 255) * amount);
            int b = Math.round((tint & 255) * (1 - amount) + (hot & 255) * amount);
            return (originalColor & 0xFF000000) | r << 16 | g << 8 | b;
        }
        // The custom glow colour belongs to the light, not to the crystal. Repainting the three
        // layers with it made the picker look like it had erased the colours underneath, and turning
        // it back off appeared to restore them from nowhere. They were never touched; they were only
        // being drawn over. The layers below always use their own colour.
        if (part == model.outerGlass) {
            return (originalColor & 0xFF000000) | (appearance.outerColor & 0xFFFFFF);
        }
        if (part == model.innerGlass) {
            return (originalColor & 0xFF000000) | (appearance.innerColor & 0xFFFFFF);
        }
        if (part == model.cube) {
            return (originalColor & 0xFF000000) | (appearance.coreColor & 0xFFFFFF);
        }
        return originalColor;
    }

    /**
     * The colour a layer shines in: its own, the core's a step towards white so it burns hottest, and
     * an unpainted layer's the glow colour, so a crystal left in its texture still glows in it.
     */
    private static int shineColour(ModelPart part, EndCrystalModel model, CrystalAppearance appearance) {
        int colour = part == model.outerGlass ? appearance.outerColor
                : part == model.innerGlass ? appearance.innerColor : appearance.coreColor;
        if ((colour & 0xFFFFFF) == 0xFFFFFF) {
            colour = appearance.haloColor();
        }
        return part == model.cube ? CrystalGlowMath.hotColor(colour) : colour;
    }

    /** {@code rgb} carrying {@code gain} of its light, premultiplied for the additive material. */
    private static int light(int rgb, float gain) {
        return CrystalGlowMath.alpha(gain) << 24
                | Math.round(((rgb >> 16) & 255) * gain) << 16
                | Math.round(((rgb >> 8) & 255) * gain) << 8
                | Math.round((rgb & 255) * gain);
    }
}
