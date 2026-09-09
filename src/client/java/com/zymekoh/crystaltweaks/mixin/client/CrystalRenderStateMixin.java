package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndCrystalRenderState.class)
public abstract class CrystalRenderStateMixin implements CrystalAppearanceAccess, CrystalGlowAccess {
    @Unique private List<CrystalGlowRenderer.Surface> crystalTweaks$surfaces = List.of();

    @Override public List<CrystalGlowRenderer.Surface> crystalTweaks$surfaces() { return crystalTweaks$surfaces; }
    @Override public void crystalTweaks$surfaces(List<CrystalGlowRenderer.Surface> surfaces) { crystalTweaks$surfaces = surfaces; }
    @Unique private CrystalAppearance crystalTweaks$appearance;

    @Override public CrystalAppearance crystalTweaks$appearance() {
        return this.crystalTweaks$appearance;
    }

    @Override public void crystalTweaks$appearance(CrystalAppearance appearance) {
        this.crystalTweaks$appearance = appearance;
    }
}
