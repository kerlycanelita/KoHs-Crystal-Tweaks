package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zymekoh.crystaltweaks.client.CrystalLayerTint;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public abstract class ModelMixin {
    @Inject(
            method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD")
    )
    private void crystalTweaks$beginCrystalTint(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            int color,
            CallbackInfo ci
    ) {
        Object model = this;
        if (model instanceof EndCrystalModel crystalModel) {
            CrystalLayerTint.begin(crystalModel);
        }
    }

    @Inject(
            method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("RETURN")
    )
    private void crystalTweaks$endCrystalTint(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            int color,
            CallbackInfo ci
    ) {
        Object model = this;
        if (model instanceof EndCrystalModel crystalModel) {
            CrystalLayerTint.end(crystalModel);
        }
    }
}
