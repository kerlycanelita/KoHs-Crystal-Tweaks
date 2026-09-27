package com.zymekoh.crystaltweaks.practice;

import java.util.Locale;

/**
 * What the player chose for Crystal Practice. The bot is given exactly the same armour, sword and
 * kit contents, so neither side starts with better gear.
 */
public final class PracticeSettings {
    public static final int HEAD = 1;
    public static final int CHEST = 2;
    public static final int LEGS = 4;
    public static final int FEET = 8;
    /** Blast Protection replaces Protection on at most this many pieces. */
    public static final int MAX_BLAST_PIECES = 2;
    /** The sword's Knockback: I is what crystal PvP kits carry, II the stronger option. */
    public static final int MIN_KNOCKBACK = 1;
    public static final int MAX_KNOCKBACK = 2;

    public enum Armor {
        NETHERITE("Netherite", "Netherite"),
        DIAMOND("Diamante", "Diamond"),
        IRON("Hierro", "Iron");

        private final String spanish;
        private final String english;

        Armor(String spanish, String english) {
            this.spanish = spanish;
            this.english = english;
        }

        public String label(boolean useSpanish) {
            return useSpanish ? this.spanish : this.english;
        }

        static Armor parse(String value) {
            for (Armor armor : values()) {
                if (armor.name().equalsIgnoreCase(value)) {
                    return armor;
                }
            }
            return NETHERITE;
        }
    }

    /**
     * How the bot plays. Every value is a number of game ticks (a twentieth of a second), and the
     * fastest are what the community documents players doing by hand: obsidian, crystal and the
     * hit on three consecutive ticks, an anchor placed, charged and set off in three.
     */
    public enum Difficulty {
        EASY("Fácil", "Easy", 9, 4, 5, 0.55F, 6.0F),
        NORMAL("Normal", "Normal", 6, 3, 3, 0.75F, 4.5F),
        HARD("Difícil", "Hard", 4, 2, 2, 0.9F, 3.5F),
        EXTREME("Extremo", "Extreme", 2, 1, 1, 1.0F, 2.5F);

        private final String spanish;
        private final String english;
        /** Ticks before re-equipping a totem, starting to eat or reacting to a hit. */
        public final int reaction;
        /** Ticks between the steps of a combo: obsidian, crystal, hit; anchor, charge, blast. */
        public final int step;
        /** Ticks between one decision and the next. */
        public final int actionDelay;
        /** How well it picks spots and aims, 0-1: lower misses the best spot now and then. */
        public final float precision;
        /** Least damage a crystal must do to the player before the bot bothers placing it. */
        public final float minimumDamage;

        Difficulty(String spanish, String english, int reaction, int step, int actionDelay, float precision,
                float minimumDamage) {
            this.spanish = spanish;
            this.english = english;
            this.reaction = reaction;
            this.step = step;
            this.actionDelay = actionDelay;
            this.precision = precision;
            this.minimumDamage = minimumDamage;
        }

        public String label(boolean useSpanish) {
            return useSpanish ? this.spanish : this.english;
        }

        public boolean has(BotSkill skill) {
            return skill.minimum.ordinal() <= ordinal();
        }

        public Difficulty next() {
            Difficulty[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static Difficulty parse(String value) {
            for (Difficulty difficulty : values()) {
                if (difficulty.name().equalsIgnoreCase(value)) {
                    return difficulty;
                }
            }
            return NORMAL;
        }
    }

    /** The ground the fight happens on. */
    public enum WorldType {
        FLAT("Plano de netherite", "Netherite flat"),
        HOLES("Hoyos", "Holes"),
        NATURAL("Natural", "Natural");

        private final String spanish;
        private final String english;

        WorldType(String spanish, String english) {
            this.spanish = spanish;
            this.english = english;
        }

        public String label(boolean useSpanish) {
            return useSpanish ? this.spanish : this.english;
        }

        static WorldType parse(String value) {
            for (WorldType type : values()) {
                if (type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return FLAT;
        }
    }

    /** The biome of a natural practice world. Each one is a world of its own. */
    public enum Biome {
        PLAINS("plains", "Pradera", "Plains"),
        DESERT("desert", "Desierto", "Desert"),
        TAIGA("taiga", "Taiga", "Taiga"),
        SNOWY("snowy_plains", "Nevado", "Snowy plains"),
        SAVANNA("savanna", "Sabana", "Savanna"),
        CHERRY("cherry_grove", "Cerezos", "Cherry grove"),
        BADLANDS("badlands", "Badlands", "Badlands"),
        END("end", "End", "End");

        /** Also the suffix of the world's save folder. */
        public final String id;
        private final String spanish;
        private final String english;

        Biome(String id, String spanish, String english) {
            this.id = id;
            this.spanish = spanish;
            this.english = english;
        }

        public String label(boolean useSpanish) {
            return useSpanish ? this.spanish : this.english;
        }

        public static Biome parse(String value) {
            for (Biome biome : values()) {
                if (biome.id.equalsIgnoreCase(value) || biome.name().equalsIgnoreCase(value)) {
                    return biome;
                }
            }
            return PLAINS;
        }
    }

    public final Armor armor;
    public final int blastPieces;
    public final boolean bot;
    public final Difficulty difficulty;
    public final int knockback;
    public final KitPreset preset;
    public final WorldType worldType;
    public final Biome biome;

    public PracticeSettings(Armor armor, int blastPieces, boolean bot, Difficulty difficulty, int knockback,
            KitPreset preset, WorldType worldType, Biome biome) {
        this.armor = armor;
        this.blastPieces = sanitize(blastPieces);
        this.bot = bot;
        this.difficulty = difficulty;
        this.knockback = clampKnockback(knockback);
        this.preset = preset;
        this.worldType = worldType;
        this.biome = biome;
    }

    /** Settings from their stored form, as the configuration file keeps them. */
    public static PracticeSettings from(String armor, int blastPieces, boolean bot, String difficulty, int knockback,
            String preset, String worldType, String biome) {
        return new PracticeSettings(Armor.parse(armor), blastPieces, bot, Difficulty.parse(difficulty), knockback,
                KitPreset.parse(preset), WorldType.parse(worldType), Biome.parse(biome));
    }

    /** The same settings on another ground, for a world whose type is fixed by its save folder. */
    public PracticeSettings on(WorldType type, Biome groundBiome) {
        return new PracticeSettings(this.armor, this.blastPieces, this.bot, this.difficulty, this.knockback, this.preset,
                type, groundBiome);
    }

    public boolean blast(int piece) {
        return (this.blastPieces & piece) != 0;
    }

    public static int clampKnockback(int level) {
        return Math.max(MIN_KNOCKBACK, Math.min(MAX_KNOCKBACK, level));
    }

    /** Keeps at most two pieces, dropping the extra ones from the feet up, as a tampered file might hold. */
    public static int sanitize(int mask) {
        int clean = mask & 0b1111;
        while (Integer.bitCount(clean) > MAX_BLAST_PIECES) {
            clean &= ~Integer.highestOneBit(clean);
        }
        return clean;
    }

    public String describe(boolean spanish) {
        StringBuilder blast = new StringBuilder();
        String[] names = spanish
                ? new String[] {"casco", "peto", "pantalones", "botas"}
                : new String[] {"helmet", "chestplate", "leggings", "boots"};
        for (int index = 0; index < 4; index++) {
            if ((this.blastPieces & (1 << index)) != 0) {
                if (blast.length() > 0) {
                    blast.append(spanish ? " y " : " and ");
                }
                blast.append(names[index]);
            }
        }
        String armorName = this.armor.label(spanish).toLowerCase(Locale.ROOT);
        String sword = spanish ? ", espada con Empuje " + roman(this.knockback) : ", sword with Knockback " + roman(this.knockback);
        if (blast.length() == 0) {
            return (spanish ? "Armadura de " + armorName + " con Protección IV" : armorName + " armour with Protection IV")
                    + sword;
        }
        return (spanish
                ? "Armadura de " + armorName + ", Protección contra explosiones IV en " + blast
                : armorName + " armour, Blast Protection IV on the " + blast) + sword;
    }

    public static String roman(int level) {
        return level >= 2 ? "II" : "I";
    }
}
