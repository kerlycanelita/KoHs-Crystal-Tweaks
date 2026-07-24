package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes vanilla's result without changing the hit, packet, slot, or input
 * timing. An accepted crystal use may create only a visual particle preview.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeCrystalPredictionMixin {
    @Unique
    private boolean kct$usingCrystal;

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void kct$captureVanillaCrystalUse(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        this.kct$usingCrystal = player.getItemInHand(hand).is(Items.END_CRYSTAL);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void kct$observeAcceptedVanillaCrystalUse(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        boolean wasUsingCrystal = this.kct$usingCrystal;
        this.kct$usingCrystal = false;
        if (wasUsingCrystal && cir.getReturnValue() instanceof InteractionResult.Success) {
            CrystalPredictor.onSuccessfulCrystalUse(hit);
        }
    }
}
