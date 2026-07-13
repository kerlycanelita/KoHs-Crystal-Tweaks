package dev.zymekoh.kohscrystaltweaks.marlow;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.mixin.PlayerInteractEntityC2SPacketAccessor;
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
import net.minecraft.world.event.GameEvent;

public final class InteractHandler {
    private final MinecraftClient client;

    public InteractHandler(MinecraftClient client) {
        this.client = client;
    }

    public void handle(PlayerInteractEntityC2SPacket packet) {
        if (this.client.world == null) {
            return;
        }

        int entityId = ((PlayerInteractEntityC2SPacketAccessor) packet).kct$getEntityId();
        Entity target = this.client.world.getEntityById(entityId);
        if (!(target instanceof EndCrystalEntity crystal)) {
            return;
        }

        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(Hand hand) {
            }

            @Override
            public void interactAt(Hand hand, Vec3d pos) {
            }

            @Override
            public void attack() {
                handleAttack(crystal);
            }
        });
    }

    private void handleAttack(EndCrystalEntity crystal) {
        if (this.client.player == null || !crystal.isAlive()) {
            return;
        }

        if (canDestroyCrystal()) {
            crystal.setRemoved(Entity.RemovalReason.KILLED);
            crystal.emitGameEvent(GameEvent.ENTITY_DIE);
            retargetCrosshair(crystal);
        }
    }

    private void retargetCrosshair(EndCrystalEntity crystal) {
        HitResult hitResult = this.client.crosshairTarget;
        boolean crosshairMatched = hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() == crystal;
        boolean targetedMatched = this.client.targetedEntity == crystal;
        if (!crosshairMatched && !targetedMatched) {
            return;
        }

        // Marlow retraces after local cleanup. This variant also excludes KoHs local
        // prediction entities, so the next physical use reaches the real block target.
        this.client.targetedEntity = null;
        this.client.crosshairTarget = CrystalPredictor.raycastIgnoringLocal(1.0f);
    }

    private boolean canDestroyCrystal() {
        return calculateTotalDamage() > 0.0;
    }

    private double calculateTotalDamage() {
        if (this.client.player == null) {
            return 0.0;
        }

        double baseDamage = this.client.player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE);
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
