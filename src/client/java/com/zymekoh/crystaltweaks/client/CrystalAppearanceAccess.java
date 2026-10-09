package com.zymekoh.crystaltweaks.client;

/** Shared by the render state and model, never by the world entity. */
public interface CrystalAppearanceAccess {
    CrystalAppearance crystalTweaks$appearance();
    void crystalTweaks$appearance(CrystalAppearance appearance);

    static CrystalAppearance of(Object target) {
        if (target instanceof CrystalAppearanceAccess access && access.crystalTweaks$appearance() != null) {
            return access.crystalTweaks$appearance();
        }
        return CrystalVisualConfig.visuals(false).copy();
    }
}
