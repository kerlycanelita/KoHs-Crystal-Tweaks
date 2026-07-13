package dev.zymekoh.kohscrystaltweaks.marlow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class InteractHandler implements PlayerInteractEntityC2SPacket.Handler {
    private final MinecraftClient client;

    public InteractHandler(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public void interact(Hand hand) {
    }

    @Override
    public void interactAt(Hand hand, Vec3d pos) {
    }

    @Override
    public void attack() {
        HitResult hitResult = this.client.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }
        if (!(entityHitResult.getEntity() instanceof EndCrystalEntity crystal)) {
            return;
        }
        if (this.client.player == null) {
            return;
        }

        if (canDestroyCrystal()) {
            crystal.setRemoved(Entity.RemovalReason.KILLED);
            if (this.client.world != null) {
                crystal.onDamaged(this.client.world.getDamageSources().genericKill());
            }
        }
    }

    private boolean canDestroyCrystal() {
        if (this.client.player == null) {
            return false;
        }

        StatusEffectInstance weakness = this.client.player.getStatusEffect(StatusEffects.WEAKNESS);
        if (weakness == null) {
            return true;
        }

        double baseDamage = this.client.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        double weaknessPenalty = 4.0 * (weakness.getAmplifier() + 1);
        if (baseDamage > weaknessPenalty + 5.0) {
            return true;
        }

        return calculateTotalDamage() > 0.0;
    }

    private double calculateTotalDamage() {
        if (this.client.player == null) {
            return 0.0;
        }

        double baseDamage = this.client.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        double weaponDamage = getWeaponDamage(this.client.player.getMainHandStack());
        StatusEffectInstance strength = this.client.player.getStatusEffect(StatusEffects.STRENGTH);
        double strengthBonus = strength != null ? 3.0 * (strength.getAmplifier() + 1) : 0.0;

        StatusEffectInstance weakness = this.client.player.getStatusEffect(StatusEffects.WEAKNESS);
        double weaknessPenalty = weakness != null ? 4.0 * (weakness.getAmplifier() + 1) : 0.0;

        return Math.max(0.0, baseDamage + weaponDamage + strengthBonus - weaknessPenalty);
    }

    private static double getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        double[] totalDamage = new double[] { 0.0 };
        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND,
                (RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) -> {
                    if (attribute.equals(EntityAttributes.ATTACK_DAMAGE)) {
                        totalDamage[0] += modifier.value();
                    }
        });
        return totalDamage[0];
    }
}
