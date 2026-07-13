package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerLocalCrystalMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void kct$blockAttackLocal(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (CrystalPredictor.isLocalCrystalEntity(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void kct$blockInteractLocal(
            PlayerEntity player,
            Entity entity,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        if (CrystalPredictor.isLocalCrystalEntity(entity)) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void kct$blockInteractAtLocal(
            PlayerEntity player,
            Entity entity,
            EntityHitResult hitResult,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        if (CrystalPredictor.isLocalCrystalEntity(entity)) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
