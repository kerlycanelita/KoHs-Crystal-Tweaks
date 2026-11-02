package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.zymekoh.crystaltweaks.client.ObsidianConverter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Converter My Obsidian's own voice: obsidian places, breaks and steps with the sounds of the block it
 * is drawn as. Sound only: a block's sounds decide nothing else.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ObsidianSoundMixin {
    @ModifyReturnValue(method = "getSoundType", at = @At("RETURN"))
    private SoundType crystalTweaks$obsidianVoice(SoundType sounds) {
        SoundType voice = ObsidianConverter.voice((BlockState) (Object) this);
        return voice == null ? sounds : voice;
    }
}
