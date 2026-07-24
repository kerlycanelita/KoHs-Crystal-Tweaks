package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Cancels only the accidental client-side start of obsidian mining while the
 * player is holding an End Crystal. It never attacks, uses an item, changes a
 * slot, or emits a packet.
 */
public final class SafeCrystalGuard {
    private static boolean initialized;

    private SafeCrystalGuard() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide()
                    || !KoHsCrystalTweaksConfig.get().safeCrystalEnabled
                    || !player.getItemInHand(hand).is(Items.END_CRYSTAL)
                    || !level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });
    }
}
