package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.KctEndCrystalRenderState;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererSeamlessMixin {
    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;getY(F)F"))
    private float kct$applyCrystalBeamFloat(float age) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        return EndCrystalRenderer.getY(
                (config.staticCrystalEnabled || !config.crystalFlotationEnabled) ? 0.0F : age);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kct$transferPredictedAnimation(
            EndCrystal entity,
            EndCrystalRenderState state,
            float partialTick,
            CallbackInfo callback) {
        KctEndCrystalRenderState extendedState = (KctEndCrystalRenderState) state;
        extendedState.kct$setHidden(false);

        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.clientSideCrystalsEnabled || !config.seamlessEnabled) {
            return;
        }

        int entityId = entity.getId();
        if (SeamlessCrystalBridge.shouldHide(entityId, CrystalPredictor.currentTick())) {
            extendedState.kct$setHidden(true);
        }
        state.ageInTicks += SeamlessCrystalBridge.ageDeltaFor(entityId);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void kct$skipHiddenRealCrystal(
            EndCrystalRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo callback) {
        if (((KctEndCrystalRenderState) state).kct$isHidden()) {
            callback.cancel();
        }
    }
}
