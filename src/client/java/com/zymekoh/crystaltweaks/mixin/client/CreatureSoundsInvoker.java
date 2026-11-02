package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A creature's own sounds, for a crystal converted into it that speaks with its voice. */
@Mixin(LivingEntity.class)
public interface CreatureSoundsInvoker {
    @Invoker("getDeathSound")
    SoundEvent crystalTweaks$deathSound();

    @Invoker("getHurtSound")
    SoundEvent crystalTweaks$hurtSound(DamageSource source);
}
