package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a mob says when it is idle: the voice a crystal converted into it speaks with as it appears. */
@Mixin(Mob.class)
public interface MobSoundsInvoker {
    @Invoker("getAmbientSound")
    SoundEvent crystalTweaks$ambientSound();
}
