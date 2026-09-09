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
        if (part == model.cube && appearance.glowPowerPercent > 0) {
            int tint = appearance.customGlowColor ? appearance.glowColor : appearance.coreColor;
            int hot = CrystalGlowMath.hotColor(tint);
            float amount = Math.min(1, CrystalGlowMath.power(appearance.glowPowerPercent));
            int r = Math.round(((tint >> 16) & 255) * (1 - amount) + ((hot >> 16) & 255) * amount);
            int g = Math.round(((tint >> 8) & 255) * (1 - amount) + ((hot >> 8) & 255) * amount);
            int b = Math.round((tint & 255) * (1 - amount) + (hot & 255) * amount);
            return (originalColor & 0xFF000000) | r << 16 | g << 8 | b;
        }
        if (appearance.customGlowColor
                && (part == model.outerGlass || part == model.innerGlass || part == model.cube)) {
            return (originalColor & 0xFF000000) | (appearance.glowColor & 0xFFFFFF);
        }
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
}
