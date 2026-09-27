package com.zymekoh.crystaltweaks.practice;

import java.util.Locale;

/**
 * What the player chose for Crystal Practice. The bot is given exactly the same, so neither side
 * starts with better gear.
 */
public final class PracticeSettings {
    public static final int HEAD = 1;
    public static final int CHEST = 2;
    public static final int LEGS = 4;
    public static final int FEET = 8;
    /** Blast Protection replaces Protection on at most this many pieces. */
    public static final int MAX_BLAST_PIECES = 2;

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
     * How the bot plays. Reaction is in ticks between noticing something and acting on it; the
     * pace caps its crystals per second the way a player's clicking would.
     */
    public enum Difficulty {
        EASY("Fácil", "Easy", 9, 7, 5, 0.55F, 6.0F),
        NORMAL("Normal", "Normal", 6, 4, 3, 0.75F, 4.5F),
        HARD("Difícil", "Hard", 4, 3, 2, 0.9F, 3.5F),
        EXTREME("Extremo", "Extreme", 2, 2, 1, 1.0F, 2.5F);

        private final String spanish;
        private final String english;
        /** Ticks before re-equipping a totem, starting to eat or reacting to a hit. */
        public final int reaction;
        /** Ticks between placing a crystal and hitting it. */
        public final int breakDelay;
        /** Ticks between one crystal action and the next. */
        public final int actionDelay;
        /** How well it picks crystal spots, 0-1: lower picks worse ones now and then. */
        public final float precision;
        /** Least damage a crystal must do to the player before the bot bothers placing it. */
        public final float minimumDamage;

        Difficulty(String spanish, String english, int reaction, int breakDelay, int actionDelay, float precision,
                float minimumDamage) {
            this.spanish = spanish;
            this.english = english;
            this.reaction = reaction;
            this.breakDelay = breakDelay;
            this.actionDelay = actionDelay;
            this.precision = precision;
            this.minimumDamage = minimumDamage;
        }

        public String label(boolean useSpanish) {
            return useSpanish ? this.spanish : this.english;
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

    public final Armor armor;
    public final int blastPieces;
    public final boolean bot;
    public final Difficulty difficulty;

    public PracticeSettings(Armor armor, int blastPieces, boolean bot, Difficulty difficulty) {
        this.armor = armor;
        this.blastPieces = sanitize(blastPieces);
        this.bot = bot;
        this.difficulty = difficulty;
    }

    /** Settings from their stored form, as the configuration file keeps them. */
    public static PracticeSettings from(String armor, int blastPieces, boolean bot, String difficulty) {
        return new PracticeSettings(Armor.parse(armor), blastPieces, bot, Difficulty.parse(difficulty));
    }

    public boolean blast(int piece) {
        return (this.blastPieces & piece) != 0;
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
        if (blast.length() == 0) {
            return spanish ? "Armadura de " + armorName + " con Protección IV" : armorName + " armour with Protection IV";
        }
        return spanish
                ? "Armadura de " + armorName + ", Protección contra explosiones IV en " + blast
                : armorName + " armour, Blast Protection IV on the " + blast;
    }
}
