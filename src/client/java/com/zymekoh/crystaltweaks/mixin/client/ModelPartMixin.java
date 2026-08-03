package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalLayerTint;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5
    )
    private int crystalTweaks$tintCrystalPart(int originalColor) {
        return CrystalLayerTint.colorFor((ModelPart) (Object) this, originalColor);
    }
}
