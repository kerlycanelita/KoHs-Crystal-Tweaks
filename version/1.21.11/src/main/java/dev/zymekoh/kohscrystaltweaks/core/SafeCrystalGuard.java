package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

/**
 * Prevents an accidental local obsidian-mining action while the player is holding an End Crystal.
 *
 * <p>{@link ActionResult#FAIL} is intentionally used on the logical client: Fabric cancels the
 * action without sending a block-attack packet. No attack, use, slot, or timing behavior is
 * synthesized.</p>
 */
public final class SafeCrystalGuard {
    private static boolean initialized;

    private SafeCrystalGuard() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient()
                    || player.isSpectator()
                    || !KoHsCrystalTweaksConfig.get().safeCrystalEnabled
                    || !player.getStackInHand(hand).isOf(Items.END_CRYSTAL)
                    || !world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                return ActionResult.PASS;
            }

            return ActionResult.FAIL;
        });
    }
}
