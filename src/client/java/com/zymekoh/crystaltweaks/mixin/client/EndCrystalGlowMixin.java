package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import com.zymekoh.crystaltweaks.client.CrystalAfterglowState;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import com.zymekoh.crystaltweaks.client.CrystalOwnership;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
        int emissiveBlockLight = CrystalGlowMath.blockLight(look.glowPowerPercent);
        state.lightCoords = (state.lightCoords & 0xFFFF0000)
                | Math.max(state.lightCoords & 0xFFFF, emissiveBlockLight);
        ((CrystalGlowAccess) state).crystalTweaks$surfaces(look.glowPowerPercent > 0
                && look.glowReflectionsPercent > 0 && state.distanceToCameraSq < 1024
                ? CrystalGlowRenderer.surfaces(entity) : List.of());
        CrystalAfterglow.observe(entity, state);
    }

    // HEAD preserves the unmodified entity origin (Vanilla later translates it for dragon beams).
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void crystalTweaks$submitGlow(EndCrystalRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        // The synthetic GUI afterglow is handled by EndCrystalRendererMixin without drawing a model.
        if (state instanceof CrystalAfterglowState) return;
        CrystalGlowRenderer.submit(state, poses, collector, camera);
    }
}
