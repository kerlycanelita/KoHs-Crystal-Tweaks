package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What builds an entity of a type. {@code EntityType.create} refuses a hostile mob while the world
 * is on Peaceful; Converter My Crystal only draws its stand-in, which never enters the world.
 */
@Mixin(EntityType.class)
public interface EntityTypeFactoryAccessor<T extends Entity> {
    @Accessor("factory")
    EntityType.EntityFactory<T> crystalTweaks$factory();
}
