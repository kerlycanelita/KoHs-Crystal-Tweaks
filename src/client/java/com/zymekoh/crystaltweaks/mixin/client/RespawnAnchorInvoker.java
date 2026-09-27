package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the Crystal Practice bot set off a charged anchor through Vanilla's own explosion: the same
 * block removal, water check, power and fire a player's click reaches. Identical from 1.21.11 to
 * 26.3, and only ever called on the practice world's server.
 */
@Mixin(RespawnAnchorBlock.class)
public interface RespawnAnchorInvoker {
    @Invoker("explode")
    void crystalTweaks$explode(BlockState state, ServerLevel level, BlockPos pos);
}
