package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalInteractionFastPath;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPassThroughLocalCrystalMixin {
    @Shadow
    public HitResult hitResult;

    @Shadow
    public LocalPlayer player;

    @Shadow
    public Options options;

    @Unique
    private boolean kct$orderedAttackHandled;

    @Shadow
    private boolean startAttack() {
        throw new AssertionError();
    }

    @Shadow
    private void startUseItem() {
        throw new AssertionError();
    }

    @Inject(
            method = "handleKeybinds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z",
                    ordinal = 0))
    private void kct$preserveCrystalInputOrder(CallbackInfo ci) {
        this.kct$orderedAttackHandled = false;
        OrderedCrystalInput.Batch batch = OrderedCrystalInput.drain();
        if (!CrystalPlacementFix.isEnabled()
                || this.player == null
                || this.player.isUsingItem()
                || batch.isEmpty()
                || !batch.requiresOrderedReplay()
                || !this.kct$isCrystalCycle(batch)) {
            return;
        }

        if (Inventory.isHotbarSlot(batch.initialSlot())) {
            this.player.getInventory().setSelectedSlot(batch.initialSlot());
        }

        for (OrderedCrystalInput.Entry entry : batch.entries()) {
            switch (entry.action()) {
                case SELECT_SLOT -> {
                    if (Inventory.isHotbarSlot(entry.selectedSlot())) {
                        this.player.getInventory().setSelectedSlot(entry.selectedSlot());
                    }
                }
                case ATTACK -> {
                    if (this.options.keyAttack.consumeClick()) {
                        this.kct$orderedAttackHandled |= this.startAttack();
                    }
                }
                case USE -> {
                    if (this.options.keyUse.consumeClick()) {
                        this.startUseItem();
                    }
                }
            }
        }
    }

    @ModifyArg(
            method = "handleKeybinds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"),
            index = 0)
    private boolean kct$suppressMiningAfterOrderedAttack(boolean breaking) {
        return this.kct$orderedAttackHandled || CrystalInteractionFastPath.shouldSuppressMining(this.hitResult)
                ? false : breaking;
    }

    @Unique
    private boolean kct$isCrystalCycle(OrderedCrystalInput.Batch batch) {
        if (this.kct$isCrystalSlot(batch.initialSlot())) {
            return true;
        }
        for (OrderedCrystalInput.Entry entry : batch.entries()) {
            if (entry.action() == OrderedCrystalInput.Action.SELECT_SLOT
                    && this.kct$isCrystalSlot(entry.selectedSlot())) {
                return true;
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            if (this.player.getItemInHand(hand).is(Items.END_CRYSTAL)
                    || this.player.getItemInHand(hand).is(Items.OBSIDIAN)) {
                return true;
            }
        }

        return this.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal;
    }

    @Unique
    private boolean kct$isCrystalSlot(int slot) {
        if (!Inventory.isHotbarSlot(slot)) {
            return false;
        }
        return this.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)
                || this.player.getInventory().getItem(slot).is(Items.OBSIDIAN);
    }

    @Redirect(
            method = "startAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"))
    private void kct$queueRapidCrystalAttack(
            MultiPlayerGameMode gameMode,
            Player player,
            Entity target) {
        if (!(target instanceof EndCrystal)
                || !CrystalPredictor.isLocalCrystalEntity(target)) {
            gameMode.attack(player, target);
            return;
        }

        EndCrystal realCrystal = CrystalPredictor.findRealCrystalForLocal(target);
        if (realCrystal != null) {
            CrystalPredictor.discardLocalCrystal(target);
            this.hitResult = new EntityHitResult(realCrystal);
            gameMode.attack(player, realCrystal);
            return;
        }

        CrystalInteractionFastPath.requestAttackForLocal(target);
        CrystalPredictor.queueLocalCrystalAttack(target);
    }

    @Redirect(
            method = "startAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean kct$queueAttackBeforeMining(
            MultiPlayerGameMode gameMode,
            BlockPos position,
            Direction direction) {
        if (CrystalInteractionFastPath.requestAttackForBase(this.player, position)) {
            return false;
        }
        return gameMode.startDestroyBlock(position, direction);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void kct$passThroughUse(CallbackInfo ci) {
        if (!(this.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }

        Entity target = entityHit.getEntity();
        if (!(target instanceof EndCrystal) || !CrystalPredictor.isLocalCrystalEntity(target)) {
            return;
        }

        this.hitResult = CrystalPredictor.raycastIgnoringLocal(1.0F);
    }
}
