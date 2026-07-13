package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.KctEndCrystalRenderState;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void kct$submitTintedCrystalModel(
            OrderedRenderCommandQueue queue,
            Model<?> modelObject,
            Object stateObject,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        EndCrystalEntityRenderState state = (EndCrystalEntityRenderState) stateObject;
        EndCrystalEntityModel model = (EndCrystalEntityModel) modelObject;
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.crystalTintEnabled) {
            kct$submitVanillaModel(queue, modelObject, state, matrices, renderLayer, light, overlay, outlineColor, crumblingOverlay);
            return;
        }

        model.setAngles(state);
        kct$submitPart(queue, model.base, matrices, renderLayer, light, overlay, outlineColor, 0xFFFFFFFF, crumblingOverlay);

        boolean innerVisible = model.innerGlass.visible;
        boolean cubeVisible = model.cube.visible;
        try {
            model.innerGlass.visible = false;
            model.cube.visible = false;
            kct$submitPart(
                    queue,
                    model.outerGlass,
                    matrices,
                    renderLayer,
                    light,
                    overlay,
                    outlineColor,
                    KoHsCrystalTweaksConfig.getCrystalFrameTintArgb(),
                    crumblingOverlay);

            model.innerGlass.visible = innerVisible;
            model.cube.visible = false;
            kct$submitNestedPart(
                    queue,
                    model.outerGlass,
                    model.innerGlass,
                    matrices,
                    renderLayer,
                    light,
                    overlay,
                    outlineColor,
                    KoHsCrystalTweaksConfig.getCrystalFrameTintArgb(),
                    crumblingOverlay);

            model.cube.visible = cubeVisible;
            kct$submitNestedPart(
                    queue,
                    model.outerGlass,
                    model.innerGlass,
                    model.cube,
                    matrices,
                    renderLayer,
                    light,
                    overlay,
                    outlineColor,
                    KoHsCrystalTweaksConfig.getCrystalCoreTintArgb(),
                    crumblingOverlay);
        } finally {
            model.innerGlass.visible = innerVisible;
            model.cube.visible = cubeVisible;
        }
    }

    private static void kct$submitPart(
            OrderedRenderCommandQueue queue,
            net.minecraft.client.model.ModelPart part,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int outlineColor,
            int tintedColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        queue.submitModelPart(
                part,
                matrices,
                renderLayer,
                light,
                overlay,
                null,
                false,
                false,
                tintedColor,
                crumblingOverlay,
                outlineColor);
    }

    @Unique
    private static void kct$submitNestedPart(
            OrderedRenderCommandQueue queue,
            ModelPart parent,
            ModelPart part,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int outlineColor,
            int tintedColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        if (!part.visible || part.hidden) {
            return;
        }

        matrices.push();
        parent.applyTransform(matrices);
        kct$submitPart(queue, part, matrices, renderLayer, light, overlay, outlineColor, tintedColor, crumblingOverlay);
        matrices.pop();
    }

    @Unique
    private static void kct$submitNestedPart(
            OrderedRenderCommandQueue queue,
            ModelPart rootParent,
            ModelPart directParent,
            ModelPart part,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int outlineColor,
            int tintedColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        if (!part.visible || part.hidden) {
            return;
        }

        matrices.push();
        rootParent.applyTransform(matrices);
        directParent.applyTransform(matrices);
        kct$submitPart(queue, part, matrices, renderLayer, light, overlay, outlineColor, tintedColor, crumblingOverlay);
        matrices.pop();
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static void kct$submitVanillaModel(
            OrderedRenderCommandQueue queue,
            Model<?> model,
            EndCrystalEntityRenderState state,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        queue.submitModel((Model<? super EndCrystalEntityRenderState>) model, state, matrices, renderLayer, light, overlay, outlineColor, crumblingOverlay);
    }
}
