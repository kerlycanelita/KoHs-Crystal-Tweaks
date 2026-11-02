package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Converter My Obsidian: obsidian drawn as another block, and with its own voice, sounding as it.
 *
 * <p>Drawing and sound only, on this screen; the block stays obsidian for the game and the server.
 * Only a solid, opaque block the game does not tint is offered, so obsidian keeps hiding exactly what
 * it hid: no wall of it turns into a window.</p>
 */
public final class ObsidianConverter {
    /** The block obsidian is drawn as, or null: read by the chunk builders on their own threads. */
    private static volatile BlockState shownAs;
    /** The sounds obsidian makes, or null for its own. */
    private static volatile SoundType voice;

    private ObsidianConverter() {
    }

    /** Whether obsidian may be drawn as this block. */
    public static boolean offered(Block block) {
        BlockState state = block.defaultBlockState();
        // Untinted: a block the game colours by biome or by state would lose its colour in obsidian's place.
        return block != Blocks.OBSIDIAN && state.isSolidRender() && state.getRenderShape() == RenderShape.MODEL && untinted(state);
    }

    private static boolean untinted(BlockState state) {
        try {
            return Minecraft.getInstance().getBlockColors().getTintSources(state).isEmpty();
        } catch (RuntimeException unknown) {
            // A colour that cannot be asked about outside a world is a colour: not offered.
            return false;
        }
    }

    /** The block to draw in place of {@code state}, or null to draw it as it is. */
    public static BlockState standIn(BlockState state) {
        BlockState shown = shownAs;
        return shown != null && state.getBlock() == Blocks.OBSIDIAN ? shown : null;
    }

    /** The sounds {@code state} makes in place of its own, or null. */
    public static SoundType voice(BlockState state) {
        SoundType sounds = voice;
        return sounds != null && state.getBlock() == Blocks.OBSIDIAN ? sounds : null;
    }

    /** The block chosen for obsidian, whether it is on or not; null while nothing fit is chosen. */
    public static BlockState chosen() {
        String target = CrystalVisualConfig.obsidianTarget();
        if (!target.startsWith(CrystalConverter.ITEM)) {
            return null;
        }
        Identifier id = Identifier.tryParse(target.substring(CrystalConverter.ITEM.length()));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        return item instanceof BlockItem block && offered(block.getBlock()) ? block.getBlock().defaultBlockState() : null;
    }

    /** The settings changed: worked out again, and the world redrawn if obsidian looks different now. */
    public static void apply() {
        BlockState before = shownAs;
        BlockState after = CrystalVisualConfig.obsidianConverterEnabled() ? chosen() : null;
        shownAs = after;
        voice = after != null && CrystalVisualConfig.obsidianVoice() ? after.getSoundType() : null;
        Minecraft minecraft = Minecraft.getInstance();
        if (before != after && minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
