package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1000)
public abstract class CrystalAfterglowRendererMixin {
    @Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("TAIL"))
    private void crystalTweaks$afterglow(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        CrystalAfterglow.submit(poses, collector, state.cameraRenderState);
    }
}
