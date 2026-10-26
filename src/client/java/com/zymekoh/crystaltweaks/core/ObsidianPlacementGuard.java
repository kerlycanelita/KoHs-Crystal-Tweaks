package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Safe Crystal: swallows the click that would mine the obsidian you are placing crystals on.
 *
 * <p>It works whatever else is installed, crystal optimizers included: the player switches it on
 * and off, nothing else does.</p>
 */
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
            if (!active()) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()
                    && level.getBlockState(position).is(Blocks.OBSIDIAN)
                    && player.getItemInHand(hand).is(Items.END_CRYSTAL)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /** Whether Safe Crystal may swallow a click right now: the player has it switched on. */
    public static boolean active() {
        return CrystalVisualConfig.safeCrystal();
    }
}
