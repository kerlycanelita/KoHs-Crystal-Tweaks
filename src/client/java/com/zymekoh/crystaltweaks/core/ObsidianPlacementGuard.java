package com.zymekoh.crystaltweaks.core;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class ObsidianPlacementGuard {
    private static boolean initialized;

    private ObsidianPlacementGuard() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
            if (level.isClientSide()
                    && level.getBlockState(position).is(Blocks.OBSIDIAN)
                    && player.getItemInHand(hand).is(Items.END_CRYSTAL)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
