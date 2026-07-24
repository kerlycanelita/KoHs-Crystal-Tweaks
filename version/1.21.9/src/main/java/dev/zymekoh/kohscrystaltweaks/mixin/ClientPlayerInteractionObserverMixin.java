package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes accepted vanilla End Crystal uses without modifying the target, result, or packet flow.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionObserverMixin {
    @Unique
    private boolean kct$usingEndCrystal;

    @Inject(
            method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"))
    private void kct$captureVanillaUse(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> callback) {
        this.kct$usingEndCrystal = player.getStackInHand(hand).isOf(Items.END_CRYSTAL);
    }

    @Inject(
            method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void kct$observeAcceptedVanillaUse(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> callback) {
        boolean usedEndCrystal = this.kct$usingEndCrystal;
        this.kct$usingEndCrystal = false;

        ActionResult result = callback.getReturnValue();
        if (usedEndCrystal && result != null && result.isAccepted()) {
            CrystalPredictor.onUseBlock(hit);
        }
    }
}
