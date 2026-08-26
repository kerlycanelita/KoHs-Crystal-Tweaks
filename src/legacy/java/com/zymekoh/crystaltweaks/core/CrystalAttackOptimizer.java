package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.mixin.client.ServerboundInteractPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Local-only prediction for a crystal the player just attacked.
 *
 * <p>The attack packet is never created, changed, delayed or duplicated here: this class runs
 * after the genuine Vanilla packet has already been handed to the connection. It only hides the
 * crystal from the local world so the break feels immediate.</p>
 *
 * <p>Because the server stays authoritative, a prediction that the server would refuse leaves an
 * entity the client can no longer see while the server still tracks it, which then silently blocks
 * every later placement on that base. The guards below only predict a break the server is certain
 * to accept, so the prediction is core behaviour rather than an option.</p>
 *
 * <p>Hiding the crystal invalidates the crosshair target Vanilla computed earlier in this tick, so
 * the pick is re-run afterwards. Without that, {@code Minecraft.startUseItem} still sees the removed
 * crystal as an {@code EntityHitResult}, takes the entity branch, fails the removed-entity range
 * check and falls through to a plain use-in-air. The placement is dropped entirely and the four tick
 * {@code rightClickDelay} is spent anyway, which is felt as a two hundred millisecond stall.</p>
 */
public final class CrystalAttackOptimizer {
    private CrystalAttackOptimizer() {
    }

    public static void handleOutgoingPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundInteractPacket interactPacket) || !isAttack(interactPacket)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int entityId = ((ServerboundInteractPacketAccessor) interactPacket).crystalTweaks$getEntityId();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> predictCrystalBreak(entityId));
            return;
        }
        predictCrystalBreak(entityId);
    }

    private static void predictCrystalBreak(int entityId) {
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

        crystal.remove(Entity.RemovalReason.KILLED);
        refreshCrosshairTarget(minecraft);
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

    /**
     * Repeats Vanilla's own crosshair pick for the current tick now that the crystal is gone.
     *
     * <p>This changes neither the player's rotation nor the ray it is cast along; it only lets
     * Vanilla re-answer the question it already answered this tick, against a world the player just
     * changed. Interaction ranges, entity selection and {@code crosshairPickEntity} all stay in
     * Vanilla's hands.</p>
     */
    private static void refreshCrosshairTarget(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.gameRenderer.pick(1.0F);
    }

    private static boolean isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
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
