package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.practice.PracticeArena;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Creates a practice world the first time and opens it every time after.
 *
 * <p>Superflat overworlds two hundred layers deep, with no structures, lakes or decoration: the
 * arena on top is built by the practice session. The netherite flat and the hole arena share one
 * world; each natural biome is a world of its own, generated in that biome so its sky, grass and
 * foliage colours are the biome's. They are ordinary singleplayer saves named "Crystal Practice
 * (Crystal Tweaks)" and "Crystal Practice · biome (Crystal Tweaks)" that the player can also open, or
 * delete, from the world list.</p>
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

    /** Leaves whatever world or server the player is in, then opens the practice world chosen. */
    public static void enter(Minecraft minecraft, Screen parent) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        String levelId = PracticeSession.levelId(settings.worldType, settings.biome);
        if (minecraft.level != null) {
            minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        try {
            if (minecraft.getLevelSource().levelExists(levelId)) {
                minecraft.createWorldOpenFlows().openWorld(levelId, () -> minecraft.setScreen(parent));
            } else {
                minecraft.createWorldOpenFlows().createFreshLevel(levelId,
                        PracticeLevelSettings.create(levelName(settings)),
                        new WorldOptions(WorldOptions.randomSeed(), false, false),
                        registries -> dimensions(registries, settings), parent);
            }
        } catch (RuntimeException failure) {
            CrystalTweaksClient.LOGGER.error("Could not open the Crystal Practice world", failure);
            minecraft.setScreen(parent);
        }
    }

    private static String levelName(PracticeSettings settings) {
        if (settings.worldType != PracticeSettings.WorldType.NATURAL) {
            return LEVEL_NAME;
        }
        return "Crystal Practice · " + settings.biome.label(false) + " (Crystal Tweaks)";
    }

    private static WorldDimensions dimensions(HolderLookup.Provider registries, PracticeSettings practice) {
        HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Holder<Biome> biome = practice.worldType == PracticeSettings.WorldType.NATURAL
                ? biomes.getOrThrow(biomeKey(practice.biome))
                : FlatLevelGeneratorSettings.getDefaultBiome(biomes);
        List<FlatLayerInfo> layers = PracticeArena.flatLayers(practice.worldType, practice.biome, PracticeSession.LAYERS);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), biome, List.of())
                .withBiomeAndLayers(layers, Optional.of(HolderSet.direct()), biome);
        return WorldPresets.createNormalWorldDimensions(registries)
                .replaceOverworldGenerator(registries, new FlatLevelSource(settings));
    }

    private static ResourceKey<Biome> biomeKey(PracticeSettings.Biome biome) {
        return switch (biome) {
            case PLAINS -> Biomes.PLAINS;
            case DESERT -> Biomes.DESERT;
            case TAIGA -> Biomes.TAIGA;
            case SNOWY -> Biomes.SNOWY_PLAINS;
            case SAVANNA -> Biomes.SAVANNA;
            case CHERRY -> Biomes.CHERRY_GROVE;
            case BADLANDS -> Biomes.BADLANDS;
            // The barrens: the End's look without the dragon fight's spikes and gateways.
            case END -> Biomes.END_BARRENS;
        };
    }
}
