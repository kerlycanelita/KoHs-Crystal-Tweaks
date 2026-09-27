package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the Crystal Practice bot carry its difficulty under its name instead of Vanilla's "NPC". */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
    @Invoker("setDescription")
    void crystalTweaks$setDescription(Component description);
}
