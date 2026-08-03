package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndCrystalRenderer.class)
public abstract class LegacyCrystalRendererMixin {
    private static final String RENDER_METHOD = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
            + "FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String PART_RENDER = "Lnet/minecraft/client/model/geom/ModelPart;render("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V";

    @ModifyArg(
            method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"),
            index = 0
    )
    private float crystalTweaks$rotationSpeed(float degrees) {
        return degrees * CrystalVisualConfig.rotationSpeedPercent() / 100.0F;
    }

    @Redirect(
            method = RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;getY("
                            + "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;F)F"
            )
    )
    private float crystalTweaks$floatingSpeed(EndCrystal crystal, float partialTick) {
        float age = (crystal.time + partialTick) * CrystalVisualConfig.floatingSpeedPercent() / 100.0F;
        float wave = Mth.sin(age * 0.2F) / 2.0F + 0.5F;
        wave = (wave * wave + wave) * 0.4F;
        return wave - 1.4F;
    }

    @Redirect(method = RENDER_METHOD, at = @At(value = "INVOKE", target = PART_RENDER, ordinal = 0))
    private void crystalTweaks$renderBase(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay
    ) {
        part.render(poseStack, vertexConsumer, light, overlay);
    }

    @Redirect(method = RENDER_METHOD, at = @At(value = "INVOKE", target = PART_RENDER, ordinal = 1))
    private void crystalTweaks$renderOuter(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay
    ) {
        part.render(poseStack, vertexConsumer, light, overlay, CrystalVisualConfig.outerColor());
    }

    @Redirect(method = RENDER_METHOD, at = @At(value = "INVOKE", target = PART_RENDER, ordinal = 2))
    private void crystalTweaks$renderInner(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay
    ) {
        part.render(poseStack, vertexConsumer, light, overlay, CrystalVisualConfig.innerColor());
    }

    @Redirect(method = RENDER_METHOD, at = @At(value = "INVOKE", target = PART_RENDER, ordinal = 3))
    private void crystalTweaks$renderCore(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay
    ) {
        part.render(poseStack, vertexConsumer, light, overlay, CrystalVisualConfig.coreColor());
    }
}
