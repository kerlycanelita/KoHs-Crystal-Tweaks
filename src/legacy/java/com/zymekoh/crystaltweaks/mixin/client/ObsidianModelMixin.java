package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zymekoh.crystaltweaks.client.ObsidianConverter;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Converter My Obsidian on 1.21.11, where a block's model is looked up in the model shaper: the chosen
 * block's model when the block is obsidian. Runs on the chunk builders' threads.
 */
@Mixin(BlockModelShaper.class)
public abstract class ObsidianModelMixin {
    @ModifyReturnValue(method = "getBlockModel", at = @At("RETURN"))
    private BlockStateModel crystalTweaks$obsidianStandIn(BlockStateModel model, @Local(argsOnly = true) BlockState state) {
        BlockState shown = ObsidianConverter.standIn(state);
        // The chosen block is never obsidian, so this asks once more and stops.
        return shown == null ? model : ((BlockModelShaper) (Object) this).getBlockModel(shown);
    }
}
