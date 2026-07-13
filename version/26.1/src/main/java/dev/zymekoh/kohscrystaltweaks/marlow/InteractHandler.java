package dev.zymekoh.kohscrystaltweaks.marlow;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Applies validated client cleanup only after vanilla has already created a real attack packet. */
public final class InteractHandler {
    private final Minecraft minecraft;

    public InteractHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void handle(ServerboundInteractPacket packet) {
        if (this.minecraft.level == null || packet.hand() != null) {
            return;
        }

        Entity target = this.minecraft.level.getEntity(packet.entityId());
        if (target instanceof EndCrystal crystal) {
            handleAttack(crystal);
        }
    }

    private void handleAttack(EndCrystal crystal) {
        if (this.minecraft.player == null || !crystal.isAlive() || !canDestroyCrystal()) {
            return;
        }

        crystal.setRemoved(Entity.RemovalReason.KILLED);
        crystal.gameEvent(GameEvent.ENTITY_DIE);
        retargetCrosshair(crystal);
    }

    private void retargetCrosshair(EndCrystal crystal) {
        HitResult hit = this.minecraft.hitResult;
        boolean crosshairMatched = hit instanceof EntityHitResult entityHit && entityHit.getEntity() == crystal;
        boolean targetedMatched = this.minecraft.crosshairPickEntity == crystal;
        if (!crosshairMatched && !targetedMatched) {
            return;
        }

        this.minecraft.crosshairPickEntity = null;
        this.minecraft.hitResult = CrystalPredictor.raycastIgnoringLocal(1.0F);
    }

    private boolean canDestroyCrystal() {
        return calculateTotalDamage() > 0.0D;
    }

    private double calculateTotalDamage() {
        if (this.minecraft.player == null) {
            return 0.0D;
        }

        double baseDamage = this.minecraft.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double weaponDamage = getWeaponDamage(this.minecraft.player.getMainHandItem());
        MobEffectInstance strength = this.minecraft.player.getEffect(MobEffects.STRENGTH);
        double strengthBonus = strength == null ? 0.0D : 3.0D * (strength.getAmplifier() + 1);
        MobEffectInstance weakness = this.minecraft.player.getEffect(MobEffects.WEAKNESS);
        double weaknessPenalty = weakness == null ? 0.0D : 4.0D * (weakness.getAmplifier() + 1);
        return Math.max(0.0D, baseDamage + weaponDamage + strengthBonus - weaknessPenalty);
    }

    private static double getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }

        double[] totalDamage = new double[] {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND,
                (Holder<Attribute> attribute, AttributeModifier modifier) -> {
                    if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                        totalDamage[0] += modifier.amount();
                    }
                });
        return totalDamage[0];
    }
}
