package com.zymekoh.crystaltweaks.practice;

import com.zymekoh.crystaltweaks.mixin.client.EntityInvulnerabilityAccessor;
import com.zymekoh.crystaltweaks.mixin.client.LivingEntityCombatAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The combat arithmetic the practice bot plans with, copied from Vanilla so its estimates match what
 * the server then does: explosion exposure and damage, armour, enchantment protection, the half
 * second of damage immunity after a hit, and knockback.
 *
 * <p>The one thing Vanilla cannot do is estimate for a place an entity has not reached yet, which is
 * what predicting a knocked-back player needs, so exposure here takes any box.</p>
 */
final class PracticeCombat {
    static final float CRYSTAL_POWER = 6.0F;
    static final float ANCHOR_POWER = 5.0F;
    /** Gravity and drag a player falls with, per tick. */
    private static final double GRAVITY = 0.08D;
    private static final double AIR_DRAG = 0.98D;
    private static final double AIR_FRICTION = 0.91D;

    private PracticeCombat() {
    }

    /**
     * Share of rays from an explosion at {@code center} that reach {@code box}: Vanilla's
     * {@code ServerExplosion.getSeenPercent}, for an arbitrary box.
     */
    static float exposure(Level level, Vec3 center, AABB box) {
        double stepX = 1.0D / ((box.maxX - box.minX) * 2.0D + 1.0D);
        double stepY = 1.0D / ((box.maxY - box.minY) * 2.0D + 1.0D);
        double stepZ = 1.0D / ((box.maxZ - box.minZ) * 2.0D + 1.0D);
        double offsetX = (1.0D - Math.floor(1.0D / stepX) * stepX) / 2.0D;
        double offsetZ = (1.0D - Math.floor(1.0D / stepZ) * stepZ) / 2.0D;
        if (stepX < 0.0D || stepY < 0.0D || stepZ < 0.0D) {
            return 0.0F;
        }
        int hits = 0;
        int count = 0;
        for (double x = 0.0D; x <= 1.0D; x += stepX) {
            for (double y = 0.0D; y <= 1.0D; y += stepY) {
                for (double z = 0.0D; z <= 1.0D; z += stepZ) {
                    Vec3 from = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offsetX, Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    if (level.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            CollisionContext.empty())).getType() == HitResult.Type.MISS) {
                        hits++;
                    }
                    count++;
                }
            }
        }
        return count == 0 ? 0.0F : (float) hits / count;
    }

    /** Raw explosion damage before armour, as Vanilla's damage calculator works it out. */
    static float rawExplosion(Level level, Vec3 center, float power, AABB box, Vec3 feet) {
        float doubleRadius = power * 2.0F;
        double distance = Math.sqrt(feet.distanceToSqr(center)) / doubleRadius;
        if (distance > 1.0D) {
            return 0.0F;
        }
        double impact = (1.0D - distance) * exposure(level, center, box);
        if (impact <= 0.0D) {
            return 0.0F;
        }
        return (float) ((impact * impact + impact) / 2.0D * 7.0D * doubleRadius + 1.0D);
    }

    /**
     * Health an explosion would take from {@code entity} standing at {@code feet}: raw damage, less
     * what its damage immunity already absorbs, through armour and enchantment protection.
     */
    static float explosionDamage(LivingEntity entity, Vec3 center, float power, Vec3 feet, PracticeSettings settings) {
        AABB box = entity.getBoundingBox().move(feet.subtract(entity.position()));
        float raw = rawExplosion(entity.level(), center, power, box, feet);
        if (raw <= 0.0F) {
            return 0.0F;
        }
        raw = afterImmunity(entity, raw);
        if (raw <= 0.0F) {
            return 0.0F;
        }
        return afterProtection(entity, afterArmor(entity, raw), explosionProtection(entity, settings));
    }

    /** Damage left once the entity's current immunity has taken its share, as LivingEntity does. */
    static float afterImmunity(LivingEntity entity, float raw) {
        if (immunity(entity) > 10) {
            return Math.max(0.0F, raw - lastHurt(entity));
        }
        return raw;
    }

    static float afterArmor(LivingEntity entity, float damage) {
        float armor = entity.getArmorValue();
        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float absorbed = Mth.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - absorbed / 25.0F);
    }

    static float afterProtection(LivingEntity entity, float damage, int protection) {
        return damage * (1.0F - Math.min(20, protection) / 25.0F);
    }

    /**
     * Enchantment protection against explosions from the armour actually worn: Protection IV gives
     * 4 and Blast Protection IV 8, capped at 20. A piece that broke gives nothing.
     */
    static int explosionProtection(LivingEntity entity, PracticeSettings settings) {
        int protection = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int[] bits = {PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET};
        for (int index = 0; index < slots.length; index++) {
            if (!entity.getItemBySlot(slots[index]).isEmpty()) {
                protection += settings.blast(bits[index]) ? 8 : 4;
            }
        }
        return Math.min(20, protection);
    }

    /** Protection against a sword: Protection IV on each piece that has it. */
    static int meleeProtection(LivingEntity entity, PracticeSettings settings) {
        int protection = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int[] bits = {PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET};
        for (int index = 0; index < slots.length; index++) {
            if (!entity.getItemBySlot(slots[index]).isEmpty() && !settings.blast(bits[index])) {
                protection += 4;
            }
        }
        return Math.min(20, protection);
    }

    static int immunity(LivingEntity entity) {
        return ((EntityInvulnerabilityAccessor) entity).crystalTweaks$invulnerableTime();
    }

    static float lastHurt(LivingEntity entity) {
        return ((LivingEntityCombatAccessor) entity).crystalTweaks$lastHurt();
    }

    static float effectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    static boolean holdsTotem(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING)
                || entity.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TOTEM_OF_UNDYING);
    }

    /**
     * Vanilla's knockback, {@code LivingEntity.knockback}, written out: the same numbers on every
     * target version, where 26.2 added parameters to the method itself.
     */
    static Vec3 knockback(LivingEntity target, double strength, double x, double z) {
        strength *= 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 motion = target.getDeltaMovement();
        if (strength <= 0.0D) {
            return motion;
        }
        if (x * x + z * z < 1.0E-5D) {
            x = 0.01D;
            z = 0.0D;
        }
        Vec3 push = new Vec3(x, 0.0D, z).normalize().scale(strength);
        return new Vec3(motion.x / 2.0D - push.x,
                target.onGround() ? Math.min(0.4D, motion.y / 2.0D + strength) : motion.y,
                motion.z / 2.0D - push.z);
    }

    /**
     * Where a player moving at {@code velocity} will be after {@code ticks}, with a player's gravity
     * and drag, landing on {@code groundY}.
     */
    static Vec3 predict(Vec3 position, Vec3 velocity, int ticks, double groundY) {
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        for (int tick = 0; tick < ticks; tick++) {
            x += vx;
            y += vy;
            z += vz;
            if (y <= groundY) {
                y = groundY;
                vy = 0.0D;
                vx *= 0.546D;
                vz *= 0.546D;
            } else {
                vy = (vy - GRAVITY) * AIR_DRAG;
                vx *= AIR_FRICTION;
                vz *= AIR_FRICTION;
            }
        }
        return new Vec3(x, y, z);
    }

    /** Durability left on a piece, 0-1; 1 for anything that does not wear. */
    static float durability(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0F;
        }
        return 1.0F - stack.getDamageValue() / (float) stack.getMaxDamage();
    }
}
