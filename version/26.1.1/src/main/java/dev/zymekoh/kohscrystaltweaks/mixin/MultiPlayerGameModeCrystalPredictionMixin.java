package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeCrystalPredictionMixin {
    @Unique
    private boolean kct$usingCrystal;

    @Unique
    private boolean kct$usingObsidian;

    @ModifyVariable(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private BlockHitResult kct$retargetFastCrystal(
            BlockHitResult hit,
            LocalPlayer player,
            InteractionHand hand) {
        return CrystalPlacementFix.retargetCrystal(player, hand, hit);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void kct$captureCrystalUse(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        this.kct$usingCrystal = player.getItemInHand(hand).is(Items.END_CRYSTAL);
        this.kct$usingObsidian = player.getItemInHand(hand).is(Items.OBSIDIAN);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void kct$predictSuccessfulCrystalUse(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        boolean wasUsingCrystal = this.kct$usingCrystal;
        boolean wasUsingObsidian = this.kct$usingObsidian;
        this.kct$usingCrystal = false;
        this.kct$usingObsidian = false;
        if (!(callback.getReturnValue() instanceof InteractionResult.Success)) {
            return;
        }
        if (wasUsingObsidian) {
            CrystalPlacementFix.recordObsidianPlacement(player, hit);
        } else if (wasUsingCrystal) {
            CrystalPredictor.onSuccessfulCrystalUse(hit);
        }
    }
}
