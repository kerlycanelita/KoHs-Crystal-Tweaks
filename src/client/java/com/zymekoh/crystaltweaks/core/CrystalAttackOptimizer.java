package com.zymekoh.crystaltweaks.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;

/**
 * Local-only prediction for a crystal the player just attacked.
 *
 * <p>The attack packet is never created, changed, delayed or duplicated here: this class runs
 * after the genuine Vanilla packet has already been handed to the connection. It only hides the
 * crystal from the local world so the break feels immediate.</p>
 *
 * <p>The server stays authoritative: the crystal is only skipped while drawing, so a prediction the
 * server refuses simply becomes visible again instead of leaving an entity the client cannot see.
 * The guards below still keep the prediction to hits the server is expected to accept.</p>
 *
 * <p>Nothing is removed from the world and the crosshair is never touched, so the hit result
 * Vanilla computed for this tick stays valid and the next click produces exactly the packet an
 * unmodified client would send.</p>
 */
public final class CrystalAttackOptimizer {
    private CrystalAttackOptimizer() {
    }

    public static void handleOutgoingPacket(Packet<?> packet) {
        // Checked before anything else, including the thread hop: while another optimizer is
        // driving crystals this must not even queue a task on the client thread.
        if (!CrystalOptimizerGuard.optimizationsAllowed()) {
            return;
        }

        if (!(packet instanceof ServerboundAttackPacket attackPacket)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int entityId = attackPacket.entityId();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> predictCrystalBreak(entityId));
            return;
        }
        predictCrystalBreak(entityId);
    }

    private static void predictCrystalBreak(int entityId) {
        // A conflict may have been detected while this task waited for the client thread.
        if (!CrystalOptimizerGuard.optimizationsAllowed()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            return;
        }

        Entity target = minecraft.level.getEntity(entityId);
        if (!(target instanceof EndCrystal crystal)
                || !isPredictableHit(player, crystal)
                || !canBreakCrystal(player)) {
            return;
        }

        CrystalBreakPrediction.markBroken(crystal, System.nanoTime());
    }

    /**
     * Mirrors the range gate Vanilla itself applies to an entity the crosshair is on, so the client
     * never hides a crystal the server is going to keep alive.
     *
     * <p>No extra safety margin is subtracted. The server accepts an attack up to three blocks
     * beyond this range, and a crystal can only reach this code when Vanilla's own pick already
     * selected it, so a stricter test here rejects ordinary hits and makes the break look slower
     * without preventing any real desynchronisation.</p>
     */
    private static boolean isPredictableHit(LocalPlayer player, EndCrystal crystal) {
        if (crystal.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        double reach = player.entityInteractionRange();
        return crystal.getBoundingBox().distanceToSqr(player.getEyePosition()) < reach * reach;
    }

    private static boolean canBreakCrystal(LocalPlayer player) {
        double damage = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        damage += heldItemDamage(player.getMainHandItem());

        MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
        if (strength != null) {
            damage += 3.0D * (strength.getAmplifier() + 1);
        }

        MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
        if (weakness != null) {
            damage -= 4.0D * (weakness.getAmplifier() + 1);
        }
        return damage > 0.0D;
    }

    private static double heldItemDamage(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return 0.0D;
        }

        double[] damage = {0.0D};
        itemStack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
                damage[0] += modifier.amount();
            }
        });
        return damage[0];
    }
}
