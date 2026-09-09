package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import java.util.List;

/** Synthetic GUI submission: a light only, never a world entity or a target. */
public final class CrystalAfterglowState extends EndCrystalRenderState implements CrystalAppearanceAccess, CrystalGlowAccess {
    public float opacity = 1;
    private CrystalAppearance appearance;
    private List<CrystalGlowRenderer.Surface> surfaces = List.of();

    @Override public CrystalAppearance crystalTweaks$appearance() { return this.appearance; }
    @Override public void crystalTweaks$appearance(CrystalAppearance appearance) { this.appearance = appearance; }
    @Override public List<CrystalGlowRenderer.Surface> crystalTweaks$surfaces() { return this.surfaces; }
    @Override public void crystalTweaks$surfaces(List<CrystalGlowRenderer.Surface> surfaces) { this.surfaces = surfaces; }
}
