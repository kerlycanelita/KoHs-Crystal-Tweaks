package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import com.zymekoh.crystaltweaks.client.CrystalAfterglowState;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalConverter;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import com.zymekoh.crystaltweaks.client.CrystalLight;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips drawing a crystal the player just attacked, so the break reads as immediate.
 *
 * <p>This is the only thing the local break prediction does. The entity itself is untouched, which
 * keeps targeting, interaction ranges and the outgoing packet stream identical to Vanilla.</p>
 *
 * <p>It also draws the crystal's light, and what Converter My Crystal puts in the crystal's place:
 * then, and only then, Vanilla's own drawing of the model is skipped. Drawing, nothing else.</p>
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
    @Shadow @Final private EndCrystalModel model;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$lightAndStandIn(EndCrystalRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        // Our synthetic preview state is the settings preview's explosion: it wears the chosen flash
        // style and size, and has no model to draw.
        if (state instanceof CrystalAfterglowState afterglow) {
            CrystalGlowRenderer.submitFlash(state, poses, collector, camera, afterglow.opacity,
                    afterglow.progress, null, List.of());
            ci.cancel();
            return;
        }
        // At HEAD the pose is still the entity's origin (Vanilla later translates it for dragon beams).
        // Converter My Crystal: something else stands where Vanilla would draw the crystal's model.
        boolean converted = CrystalConverter.submit(CrystalAppearanceAccess.of(state), state, poses, collector, camera);
        CrystalGlowRenderer.submit(state, poses, collector, camera);
        CrystalLight.submit(this.model, state, poses, collector, camera.orientation, converted);
        if (converted) {
            ci.cancel();
        }
    }
    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
                    + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void crystalTweaks$hidePredictedBreak(
            EndCrystal crystal,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (CrystalBreakPrediction.isHidden(crystal)) {
            CrystalAfterglow.onHidden(crystal);
            callback.setReturnValue(false);
        }
    }
}
