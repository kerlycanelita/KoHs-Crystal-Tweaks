package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The damage an entity is currently immune up to. Within half a second of a hit Minecraft only
 * applies what a new hit exceeds this by, which is what the practice bot times its second crystal,
 * the d-tap, around.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityCombatAccessor {
    @Accessor("lastHurt")
    float crystalTweaks$lastHurt();
}
