package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips drawing a crystal the player just attacked, so the break reads as immediate.
 *
 * <p>This is the only thing the local break prediction does. The entity itself is untouched, which
 * keeps targeting, interaction ranges and the outgoing packet stream identical to Vanilla.</p>
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
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
            callback.setReturnValue(false);
        }
    }
}
