package com.zymekoh.crystaltweaks.client.practice;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;

/** 1.21.11's constructor for the practice world's level settings; see the 26.x copy. */
final class PracticeLevelSettings {
    private PracticeLevelSettings() {
    }

    static LevelSettings create(String name) {
        return new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
    }
}
