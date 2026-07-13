package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerLocalCrystalMixin {
    @Unique
    private boolean kct$usingCrystal;

    @Unique
    private boolean kct$usingObsidian;

    @ModifyVariable(
            method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private BlockHitResult kct$retargetFastCrystal(
            BlockHitResult hit,
            ClientPlayerEntity player,
            Hand hand) {
        return CrystalPlacementFix.retargetCrystal(player, hand, hit);
    }

    @Inject(
            method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"))
    private void kct$captureBlockUse(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> callback) {
        this.kct$usingCrystal = player.getStackInHand(hand).isOf(Items.END_CRYSTAL);
        this.kct$usingObsidian = player.getStackInHand(hand).isOf(Items.OBSIDIAN);
    }

    @Inject(
            method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void kct$handleSuccessfulBlockUse(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> callback) {
        boolean wasUsingCrystal = this.kct$usingCrystal;
        boolean wasUsingObsidian = this.kct$usingObsidian;
        this.kct$usingCrystal = false;
        this.kct$usingObsidian = false;

        ActionResult result = callback.getReturnValue();
        if (result == null || !result.isAccepted()) {
            return;
        }
        if (wasUsingObsidian) {
            CrystalPlacementFix.recordObsidianPlacement(player, hit);
        } else if (wasUsingCrystal) {
            CrystalPredictor.onUseBlock(hit);
        }
    }

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
