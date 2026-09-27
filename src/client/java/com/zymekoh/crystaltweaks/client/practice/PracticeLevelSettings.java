package com.zymekoh.crystaltweaks.client.practice;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;

/**
 * The practice world's level settings: survival, Normal difficulty, commands allowed. Minecraft
 * 26.1 made {@link LevelSettings} a record with a difficulty group and moved the game rules out of
 * it, so 1.21.11 has its own copy of this class under {@code src/legacy}.
 */
final class PracticeLevelSettings {
    private PracticeLevelSettings() {
    }

    static LevelSettings create(String name) {
        return new LevelSettings(name, GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true,
                WorldDataConfiguration.DEFAULT);
    }
}
