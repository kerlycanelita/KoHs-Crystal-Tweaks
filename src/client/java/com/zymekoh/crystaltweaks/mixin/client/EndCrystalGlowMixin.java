package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import com.zymekoh.crystaltweaks.client.CrystalOwnership;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.util.List;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalGlowMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
    private void crystalTweaks$extractAppearance(EndCrystal entity, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
        CrystalAppearance look = CrystalVisualConfig.visuals(CrystalOwnership.useOtherProfile(entity)).copy();
        ((CrystalAppearanceAccess) state).crystalTweaks$appearance(look);
        // The crystal's own light belongs to the glow: with the glow switched off it is lit like Vanilla.
        int emissiveBlockLight = CrystalGlowMath.blockLight(look.glowActive() ? look.glowPowerPercent : 0);
        state.lightCoords = (state.lightCoords & 0xFFFF0000)
                | Math.max(state.lightCoords & 0xFFFF, emissiveBlockLight);
        float spill = CrystalVisualConfig.glowQuality().spillRange;
        ((CrystalGlowAccess) state).crystalTweaks$surfaces(look.glowActive()
                && look.glowReflectionsPercent > 0 && state.distanceToCameraSq < spill * spill
                ? CrystalGlowRenderer.surfaces(entity) : List.of());
        CrystalAfterglow.observe(entity, state);
    }
}
