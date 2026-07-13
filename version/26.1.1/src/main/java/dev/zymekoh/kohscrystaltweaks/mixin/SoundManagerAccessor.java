package dev.zymekoh.kohscrystaltweaks.mixin;

import java.util.Map;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("registry")
    Map<Identifier, WeighedSoundEvents> kct$getSounds();

    @Accessor("soundEngine")
    SoundEngine kct$getSoundSystem();
}
