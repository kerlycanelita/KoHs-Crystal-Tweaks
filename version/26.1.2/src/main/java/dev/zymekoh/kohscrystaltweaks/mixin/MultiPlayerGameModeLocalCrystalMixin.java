package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.CrystalInteractionFastPath;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeLocalCrystalMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void kct$doNotSendAttackForPrediction(Player player, Entity target, CallbackInfo callback) {
        if (CrystalPredictor.isLocalCrystalEntity(target)
                || !CrystalInteractionFastPath.claimRealCrystalAttack(target)) {
            callback.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void kct$doNotInteractWithPrediction(
            Player player,
            Entity target,
            EntityHitResult hit,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (CrystalPredictor.isLocalCrystalEntity(target)) {
            callback.setReturnValue(InteractionResult.PASS);
        }
    }
}
