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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CrystalAttackOptimizer {
    private CrystalAttackOptimizer() {
    }

    public static void handleOutgoingPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundInteractPacket interactPacket) || !isAttack(interactPacket)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        int entityId = ((ServerboundInteractPacketAccessor) interactPacket).crystalTweaks$getEntityId();
        Entity target = minecraft.level.getEntity(entityId);
        if (!(target instanceof EndCrystal crystal) || !canBreakCrystal(minecraft.player)) {
            return;
        }

        crystal.remove(Entity.RemovalReason.KILLED);
        crystal.gameEvent(GameEvent.ENTITY_DIE);
        refreshTarget(minecraft, crystal);
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

    private static void refreshTarget(Minecraft minecraft, EndCrystal crystal) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.hitResult == null || minecraft.crosshairPickEntity != crystal) {
            return;
        }

        HitResult refreshed = player.pick(player.blockInteractionRange(), 1.0F, false);
        minecraft.crosshairPickEntity = null;
        minecraft.hitResult = refreshed;
    }
}
