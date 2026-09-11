package com.zymekoh.crystaltweaks.core;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Safe Crystal: swallows the click that would mine the obsidian you are placing crystals on.
 *
 * <p>This is an interaction helper, so it follows the same rule as every other one. With Marlow's
 * Crystal Optimizer, No Crystal Break or any other detected crystal optimizer installed, it stands
 * down completely and the click reaches Vanilla untouched: that mod owns the crystal click, and two
 * mods deciding whether the same swing mines a block is how a player ends up unable to break
 * obsidian at all.</p>
 *
 * <p>A performance mod is not that. Krypton, Lithium, Sodium, ViaFabricPlus and the rest never touch
 * the crystal click, so Safe Crystal keeps working next to them; {@link CrystalOptimizerGuard} names
 * them explicitly so no Mixin overlap can be mistaken for a rival.</p>
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

    /**
     * Whether Safe Crystal may swallow a click right now.
     *
     * <p>Named rather than inlined so the stand-down is one decision with one reason, and so the
     * settings screen and the tests can ask the same question the event handler asks.</p>
     */
    public static boolean active() {
        return CrystalOptimizerGuard.optimizationsAllowed();
    }
}
