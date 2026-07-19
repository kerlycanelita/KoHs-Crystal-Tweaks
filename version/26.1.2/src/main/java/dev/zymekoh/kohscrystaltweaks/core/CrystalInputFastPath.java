package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.mixin.MinecraftInputInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Dispatches one exclusive physical crystal input during its callback instead of waiting for the
 * next client tick. The matching vanilla counter and ordered entry are consumed, so this never
 * repeats, fabricates, or automates an input. Shared attack/use/hotbar bindings stay on replay.
 */
public final class CrystalInputFastPath {
    private CrystalInputFastPath() {
    }

    public static void onPhysicalInput(boolean attack, boolean use, boolean hotbarSelection) {
        if (!isExclusiveAction(attack, use, hotbarSelection)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || minecraft.screen != null
                || minecraft.getOverlay() != null
                || minecraft.player.isUsingItem()) {
            return;
        }

        MinecraftInputInvoker invoker = (MinecraftInputInvoker) (Object) minecraft;
        invoker.kct$invokePick(1.0F);

        if (use) {
            dispatchUse(minecraft, invoker);
        } else {
            dispatchAttack(minecraft, invoker);
        }
    }

    private static void dispatchUse(Minecraft minecraft, MinecraftInputInvoker invoker) {
        if (minecraft.hitResult == null
                || minecraft.gameMode.isDestroying()
                || !holdsEndCrystal(minecraft)
                || !OrderedCrystalInput.claimExclusiveActionForImmediateDispatch(
                        OrderedCrystalInput.Action.USE)) {
            return;
        }

        if (minecraft.options.keyUse.consumeClick()) {
            invoker.kct$invokeStartUseItem();
        }
    }

    private static void dispatchAttack(Minecraft minecraft, MinecraftInputInvoker invoker) {
        if (!isCrystalAttackTarget(minecraft)
                || !OrderedCrystalInput.claimExclusiveActionForImmediateDispatch(
                        OrderedCrystalInput.Action.ATTACK)) {
            return;
        }

        if (minecraft.options.keyAttack.consumeClick()) {
            invoker.kct$invokeStartAttack();
        }
    }

    private static boolean holdsEndCrystal(Minecraft minecraft) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (minecraft.player.getItemInHand(hand).is(Items.END_CRYSTAL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCrystalAttackTarget(Minecraft minecraft) {
        return (minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal)
                || CrystalInteractionFastPath.hasRecentPlacementBase(minecraft.hitResult);
    }

    static boolean isExclusiveAction(boolean attack, boolean use, boolean hotbarSelection) {
        return attack != use && !hotbarSelection;
    }
}
