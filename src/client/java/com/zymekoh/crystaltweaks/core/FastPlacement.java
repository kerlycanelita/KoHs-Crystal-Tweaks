package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.client.fight.PlacementDiagnostic;
import com.zymekoh.crystaltweaks.mixin.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Placement at the server's pace: what Marlow's optimizer is after, without ever getting ahead of
 * the server.
 *
 * <p>Right after you hit a crystal it stays in your world until the server removes it, so a Use
 * click in that gap lands on the dying crystal: Vanilla spends it on the entity and nothing is
 * placed, and you have to click again once it is gone. Marlow's removes the crystal on your screen
 * at the hit so the click reaches the obsidian a round trip early, which changes the packets the
 * client sends and is why servers block it.</p>
 *
 * <p>Here the crystal is never removed early and nothing is sent ahead of the server. A Use click
 * that would land on a crystal you just broke is held instead of being spent on it. The moment the
 * server's removal arrives, the click is handed back to Vanilla's own key handling, so on that tick
 * it does exactly what a click would: the same placement packet, the same order within the tick,
 * the same rotation, the same reach, from a world where the server has already removed the
 * crystal. Nothing is ever sent that a Vanilla client could not send at that moment, at any ping.</p>
 *
 * <p>One click is one placement at most. If you look away from that obsidian, or the server keeps
 * the crystal and the wait runs out, the click is simply dropped, as Vanilla would have spent it on
 * the crystal. It changes when your click reaches the server, so it is an input aid and off until
 * you turn it on.</p>
 */
public final class FastPlacement {
    /** How long a released click stays valid: the tick it lands on and one more. */
    private static final long RELEASE_NANOS = 120_000_000L;

    private static int pendingCrystal = -1;
    private static BlockPos pendingBase;
    private static long pendingDeadline;
    private static BlockPos releaseBase;
    private static long releaseUntil;
    private static int held;
    private static int sent;
    private static int dropped;

    private FastPlacement() {
    }

    /**
     * For each Use click Vanilla's key handling is about to spend: true when Crystal Tweaks held it
     * for the server (or dropped a released click the player no longer aims), false to let Vanilla
     * use it now.
     */
    public static boolean holdUse(Minecraft minecraft) {
        long now = System.nanoTime();
        if (releaseBase != null) {
            // The click handed back at the removal: it lands on that obsidian, or nowhere.
            BlockPos base = releaseBase;
            releaseBase = null;
            if (now <= releaseUntil) {
                HitResult hit = minecraft.hitResult;
                if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(base)) {
                    sent++;
                    return false;
                }
                dropped++;
                return true;
            }
        }
        if (!CrystalCore.fastPlacement()) {
            if (CrystalCore.active() && minecraft.player != null && aimsAtBrokenCrystal(minecraft) && holdsCrystal(minecraft.player)) {
                // Vanilla is about to spend this click on the dying crystal: say so, and what keeps it.
                PlacementDiagnostic.spentOnBreaking(now);
            }
            return false;
        }
        if (pendingCrystal >= 0 || minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return false;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof EndCrystal crystal)
                || !CrystalBreakPrediction.isPredicted(crystal.getId()) || !holdsCrystal(minecraft.player)) {
            return false;
        }
        BlockPos base = BlockPos.containing(crystal.getX(), crystal.getY() - 1.0D, crystal.getZ());
        BlockState state = minecraft.level.getBlockState(base);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return false;
        }
        pendingCrystal = crystal.getId();
        pendingBase = base.immutable();
        pendingDeadline = now + CrystalBreakPrediction.predictionWindowNanos();
        held++;
        return true;
    }

    private static boolean aimsAtBrokenCrystal(Minecraft minecraft) {
        return minecraft.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystal crystal
                && CrystalBreakPrediction.isPredicted(crystal.getId());
    }

    private static boolean holdsCrystal(LocalPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.END_CRYSTAL)
                || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.END_CRYSTAL);
    }

    /**
     * The server removed a crystal, on the client thread. When the held click was waiting for it,
     * it goes back into the Use key as one click, for Vanilla's own handling to spend on its next
     * tick, after it has looked again at what the crosshair is on.
     */
    public static void removed(int entityId) {
        if (entityId != pendingCrystal) {
            return;
        }
        releaseBase = pendingBase;
        releaseUntil = System.nanoTime() + RELEASE_NANOS;
        clearPending();
        KeyMapping use = Minecraft.getInstance().options.keyUse;
        KeyMappingAccessor clicks = (KeyMappingAccessor) use;
        clicks.crystalTweaks$setClickCount(clicks.crystalTweaks$clickCount() + 1);
    }

    /** Each client tick: a click whose crystal the server kept is dropped when the wait runs out. */
    public static void tick(long now) {
        if (pendingCrystal >= 0 && now > pendingDeadline) {
            dropped++;
            clearPending();
        }
        if (releaseBase != null && now > releaseUntil) {
            releaseBase = null;
        }
    }

    private static void clearPending() {
        pendingCrystal = -1;
        pendingBase = null;
    }

    public static void reset() {
        clearPending();
        releaseBase = null;
    }

    /** Clicks held, sent when the server removed the crystal, and dropped, since the game started. */
    public static int held() {
        return held;
    }

    public static int sent() {
        return sent;
    }

    public static int dropped() {
        return dropped;
    }
}
