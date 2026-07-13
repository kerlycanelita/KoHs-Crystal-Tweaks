package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.KctEndCrystalRenderState;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
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
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$cancelHiddenCrystalRender(
            EndCrystalEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (((KctEndCrystalRenderState) state).kct$isHidden()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;getYOffset(F)F"))
    private float kct$applyCrystalBeamFloat(float age) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        return EndCrystalEntityRenderer.getYOffset((config.staticCrystalEnabled || !config.crystalFlotationEnabled) ? 0.0f : age);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EndCrystalEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
    private void kct$renderCrystalModelWithTint(
            EndCrystalEntityModel model,
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.crystalTintEnabled) {
            model.render(matrices, vertices, light, overlay);
            return;
        }

        kct$renderPart(model.base, matrices, vertices, light, overlay, 0xFFFFFFFF);

        boolean innerVisible = model.innerGlass.visible;
        boolean cubeVisible = model.cube.visible;
        try {
            model.innerGlass.visible = false;
            model.cube.visible = false;
            kct$renderPart(
                    model.outerGlass,
                    matrices,
                    vertices,
                    light,
                    overlay,
                    KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());

            model.innerGlass.visible = innerVisible;
            model.cube.visible = false;
            kct$renderNestedPart(
                    model.outerGlass,
                    model.innerGlass,
                    matrices,
                    vertices,
                    light,
                    overlay,
                    KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());

            model.cube.visible = cubeVisible;
            kct$renderNestedPart(
                    model.outerGlass,
                    model.innerGlass,
                    model.cube,
                    matrices,
                    vertices,
                    light,
                    overlay,
                    KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
        } finally {
            model.innerGlass.visible = innerVisible;
            model.cube.visible = cubeVisible;
        }
    }

    @Unique
    private static void kct$renderPart(
            ModelPart part,
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color) {
        if (!part.visible || part.hidden) {
            return;
        }
        part.render(matrices, vertices, light, overlay, color);
    }

    @Unique
    private static void kct$renderNestedPart(
            ModelPart parent,
            ModelPart part,
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color) {
        if (!part.visible || part.hidden) {
            return;
        }

        matrices.push();
        parent.rotate(matrices);
        part.render(matrices, vertices, light, overlay, color);
        matrices.pop();
    }

    @Unique
    private static void kct$renderNestedPart(
            ModelPart rootParent,
            ModelPart directParent,
            ModelPart part,
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color) {
        if (!part.visible || part.hidden) {
            return;
        }

        matrices.push();
        rootParent.rotate(matrices);
        directParent.rotate(matrices);
        part.render(matrices, vertices, light, overlay, color);
        matrices.pop();
    }
}
