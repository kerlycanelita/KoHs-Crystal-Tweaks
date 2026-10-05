package com.zymekoh.crystaltweaks.client;

/** Shared by the render state and model, never by the world entity. */
public interface CrystalAppearanceAccess {
    CrystalAppearance crystalTweaks$appearance();
    void crystalTweaks$appearance(CrystalAppearance appearance);

    /** On the crystal model: the glow pass it was last posed for, or {@code null} for its own drawing. */
    default CrystalLayerGlowState crystalTweaks$glowPass() {
        return null;
    }

    static CrystalAppearance of(Object target) {
        if (target instanceof CrystalAppearanceAccess access && access.crystalTweaks$appearance() != null) {
            return access.crystalTweaks$appearance();
        }
        return CrystalVisualConfig.visuals(false).copy();
    }
}
