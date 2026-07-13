package dev.zymekoh.kohscrystaltweaks.mixin;

import java.util.Map;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("sounds")
    Map<Identifier, WeightedSoundSet> kct$getSounds();

    @Accessor("soundSystem")
    SoundSystem kct$getSoundSystem();
}
