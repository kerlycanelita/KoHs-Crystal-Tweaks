package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Creates the practice world the first time and opens it every time after.
 *
 * <p>A superflat overworld: one bedrock layer, then 198 layers of stone and dirt, then a floor of
 * obsidian two hundred layers up, so a crystal can go anywhere and no blast digs a crater. No
 * structures, no lakes, plains biome. It is an ordinary singleplayer save named "Crystal Practice
 * (Crystal Tweaks)" that the player can also open, or delete, from the world list.</p>
 */
public final class PracticeWorld {
    public static final String LEVEL_NAME = "Crystal Practice (Crystal Tweaks)";

    private static boolean initialized;

    private PracticeWorld() {
    }

    /**
     * Keeps the practice world free of toasts. Every visit hands out a full kit, which Vanilla
     * greets with a stack of advancement and recipe toasts over the corner of the fight.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.level != null && PracticeSession.running()) {
                minecraft.getToastManager().clear();
            }
        });
    }

    /** Leaves whatever world or server the player is in, then opens the practice world. */
    public static void enter(Minecraft minecraft, Screen parent) {
        if (minecraft.level != null) {
            minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        try {
            if (minecraft.getLevelSource().levelExists(PracticeSession.LEVEL_ID)) {
                minecraft.createWorldOpenFlows().openWorld(PracticeSession.LEVEL_ID, () -> minecraft.setScreen(parent));
            } else {
                minecraft.createWorldOpenFlows().createFreshLevel(PracticeSession.LEVEL_ID,
                        PracticeLevelSettings.create(LEVEL_NAME), new WorldOptions(WorldOptions.randomSeed(), false, false),
                        PracticeWorld::dimensions, parent);
            }
        } catch (RuntimeException failure) {
            CrystalTweaksClient.LOGGER.error("Could not open the Crystal Practice world", failure);
            minecraft.setScreen(parent);
        }
    }

    private static WorldDimensions dimensions(HolderLookup.Provider registries) {
        HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Holder<Biome> plains = FlatLevelGeneratorSettings.getDefaultBiome(biomes);
        List<FlatLayerInfo> layers = List.of(
                new FlatLayerInfo(1, Blocks.BEDROCK),
                new FlatLayerInfo(PracticeSession.LAYERS - 6, Blocks.STONE),
                new FlatLayerInfo(4, Blocks.DIRT),
                new FlatLayerInfo(1, Blocks.OBSIDIAN));
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), plains, List.of())
                .withBiomeAndLayers(layers, Optional.of(HolderSet.direct()), plains);
        return WorldPresets.createNormalWorldDimensions(registries)
                .replaceOverworldGenerator(registries, new FlatLevelSource(settings));
    }
}
