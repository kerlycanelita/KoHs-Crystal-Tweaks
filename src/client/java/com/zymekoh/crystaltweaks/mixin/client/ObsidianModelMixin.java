package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zymekoh.crystaltweaks.client.ObsidianConverter;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Converter My Obsidian: the model the chunk builders, the breaking particles and every other drawing
 * of a block look up is the chosen block's when the block is obsidian. Runs on the builders' threads.
 */
@Mixin(BlockStateModelSet.class)
public abstract class ObsidianModelMixin {
    @Shadow
    @Final
    private Map<BlockState, BlockStateModel> modelByState;

    @Shadow
    @Final
    private BlockStateModel missingModel;

    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private BlockStateModel crystalTweaks$obsidianStandIn(BlockStateModel model, @Local(argsOnly = true) BlockState state) {
        BlockState shown = ObsidianConverter.standIn(state);
        return shown == null ? model : this.modelByState.getOrDefault(shown, this.missingModel);
    }
}
