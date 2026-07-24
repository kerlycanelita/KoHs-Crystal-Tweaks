package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.KctEndCrystalRenderState;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererSeamlessMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V",
            at = @At("TAIL"))
    private void kct$updateRenderState(
            EndCrystalEntity entity,
            EndCrystalEntityRenderState state,
            float tickDelta,
            CallbackInfo ci) {
        KctEndCrystalRenderState hiddenState = (KctEndCrystalRenderState) state;
        hiddenState.kct$setHidden(false);

        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.clientSideCrystalsEnabled || !config.seamlessEnabled) {
            return;
        }

        int entityId = entity.getId();
        if (SeamlessCrystalBridge.shouldHide(entityId, CrystalPredictor.debugTick())) {
            hiddenState.kct$setHidden(true);
            return;
        }

        int delta = SeamlessCrystalBridge.ageDeltaFor(entityId);
        if (delta != 0) {
            state.age += delta;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$cancelHiddenCrystalRender(
            EndCrystalEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci) {
        if (((KctEndCrystalRenderState) state).kct$isHidden()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;getYOffset(F)F"))
    private float kct$applyCrystalBeamFloat(float age) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        return EndCrystalEntityRenderer.getYOffset((config.staticCrystalEnabled || !config.crystalFlotationEnabled) ? 0.0f : age);
    }

}
