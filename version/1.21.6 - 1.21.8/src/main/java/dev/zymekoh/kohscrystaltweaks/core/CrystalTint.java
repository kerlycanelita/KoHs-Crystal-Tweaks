package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.ModelPart;

public final class CrystalTint {
    private static final Map<ModelPart, TintRole> PART_ROLES = new WeakHashMap<>();

    private CrystalTint() {
    }

    public static void register(ModelPart outerGlass, ModelPart innerGlass, ModelPart cube) {
        PART_ROLES.put(outerGlass, TintRole.FRAME);
        PART_ROLES.put(innerGlass, TintRole.FRAME);
        PART_ROLES.put(cube, TintRole.CORE);
    }

    public static int colorFor(ModelPart part, int originalColor) {
        if (!KoHsCrystalTweaksConfig.get().crystalTintEnabled) {
            return originalColor;
        }

        TintRole role = PART_ROLES.get(part);
        if (role == null) {
            return originalColor;
        }
        return role == TintRole.FRAME
                ? KoHsCrystalTweaksConfig.getCrystalFrameTintArgb()
                : KoHsCrystalTweaksConfig.getCrystalCoreTintArgb();
    }

    private enum TintRole {
        FRAME,
        CORE
    }
}
