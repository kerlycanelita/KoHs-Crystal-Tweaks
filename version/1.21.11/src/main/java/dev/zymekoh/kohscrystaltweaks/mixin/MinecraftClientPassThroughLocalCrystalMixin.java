package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientPassThroughLocalCrystalMixin {
    @Shadow
    public HitResult crosshairTarget;

    @Shadow
    public ClientPlayerEntity player;

    @Shadow
    public GameOptions options;

    @Unique
    private boolean kct$orderedAttackHandled;

    @Shadow
    private boolean doAttack() {
        throw new AssertionError();
    }

    @Shadow
    private void doItemUse() {
        throw new AssertionError();
    }

    @Inject(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                    ordinal = 0))
    private void kct$preserveCrystalInputOrder(CallbackInfo ci) {
        this.kct$orderedAttackHandled = false;
        if (!CrystalPlacementFix.isEnabled()
                || this.player == null
                || this.player.isUsingItem()
                || !this.kct$isCrystalCycle()) {
            OrderedCrystalInput.clear();
            return;
        }

        for (OrderedCrystalInput.Action action : OrderedCrystalInput.drain()) {
            if (action == OrderedCrystalInput.Action.ATTACK) {
                if (this.options.attackKey.wasPressed()) {
                    this.kct$orderedAttackHandled |= this.doAttack();
                }
            } else if (this.options.useKey.wasPressed()) {
                this.doItemUse();
            }
        }
    }

    @ModifyArg(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;handleBlockBreaking(Z)V"),
            index = 0)
    private boolean kct$suppressMiningAfterOrderedAttack(boolean breaking) {
        return this.kct$orderedAttackHandled ? false : breaking;
    }

    @Unique
    private boolean kct$isCrystalCycle() {
        for (Hand hand : Hand.values()) {
            if (this.player.getStackInHand(hand).isOf(Items.END_CRYSTAL)
                    || this.player.getStackInHand(hand).isOf(Items.OBSIDIAN)) {
                return true;
            }
        }

        return this.crosshairTarget instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystalEntity;
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void kct$passThroughAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.crosshairTarget == null || this.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) this.crosshairTarget).getEntity();

        if (!(target instanceof EndCrystalEntity) || !CrystalPredictor.isLocalCrystalEntity(target)) {
            return;
        }

        if (KoHsCrystalTweaksConfig.get().rapidAttackFixEnabled) {
            return;
        }

        CrystalPredictor.onLocalCrystalAttack(target);
        this.crosshairTarget = CrystalPredictor.raycastIgnoringLocal(1.0f);
    }

    @Redirect(
            method = "doAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V"))
    private void kct$queueRapidCrystalAttack(
            ClientPlayerInteractionManager interactionManager,
            PlayerEntity player,
            Entity target) {
        if (!KoHsCrystalTweaksConfig.get().rapidAttackFixEnabled
                || !(target instanceof EndCrystalEntity)
                || !CrystalPredictor.isLocalCrystalEntity(target)) {
            interactionManager.attackEntity(player, target);
            return;
        }

        EndCrystalEntity realCrystal = CrystalPredictor.findRealCrystalForLocal(target);
        if (realCrystal != null) {
            CrystalPredictor.discardLocalCrystal(target);
            this.crosshairTarget = new EntityHitResult(realCrystal);
            interactionManager.attackEntity(player, realCrystal);
            return;
        }

        CrystalPredictor.queueLocalCrystalAttack(target);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void kct$passThroughUse(CallbackInfo ci) {
        if (this.crosshairTarget == null || this.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) this.crosshairTarget).getEntity();
        if (!(target instanceof EndCrystalEntity) || !CrystalPredictor.isLocalCrystalEntity(target)) {
            return;
        }

        this.crosshairTarget = CrystalPredictor.raycastIgnoringLocal(1.0f);
    }
}
