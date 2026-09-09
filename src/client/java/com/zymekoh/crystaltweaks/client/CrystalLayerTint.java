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
        // A glow is one color by definition, so it takes over from the three layer colors rather
        // than trying to blend with them. The settings screen warns before this is switched on.
        if (CrystalVisualConfig.customGlowColor()
                && (part == model.outerGlass || part == model.innerGlass || part == model.cube)) {
            return CrystalVisualConfig.glowColor();
        }
        if (part == model.outerGlass) {
            return CrystalVisualConfig.outerColor();
        }
        if (part == model.innerGlass) {
            return CrystalVisualConfig.innerColor();
        }
        if (part == model.cube) {
            return CrystalVisualConfig.coreColor();
        }
        return originalColor;
    }
}
