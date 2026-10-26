package com.zymekoh.crystaltweaks.practice;

/** An exercise with a goal and a clock, on top of the usual practice. */
public enum PracticeDrill {
    NONE("Ninguno", "None", "", ""),
    /** Break 20 crystals as fast as you can: placing and hitting, nothing else counts. */
    SPEED("Velocidad: 20 cristales", "Speed: 20 crystals",
            "Rompe 20 cristales lo más rápido que puedas. El reloj empieza con el primero.",
            "Break 20 crystals as fast as you can. The clock starts with the first one."),
    /** Make the bot pop five totems, the sooner the better. */
    TOTEMS("Revienta 5 tótems", "Pop 5 totems",
            "Haz que el bot gaste 5 tótems. El reloj empieza con la ronda.",
            "Make the bot use up 5 totems. The clock starts with the round."),
    /** Stay alive for a minute against the bot. */
    SURVIVE("Sobrevive 60 s", "Survive 60 s",
            "Aguanta un minuto contra el bot. Cuenta los tótems que gastaste.",
            "Hold out for a minute against the bot. Counts the totems you used.");

    /** Crystals the speed drill asks for, totems the totem drill asks for, seconds the survival drill lasts. */
    public static final int SPEED_GOAL = 20;
    public static final int TOTEM_GOAL = 5;
    public static final int SURVIVE_SECONDS = 60;

    private final String spanish;
    private final String english;
    private final String spanishNote;
    private final String englishNote;

    PracticeDrill(String spanish, String english, String spanishNote, String englishNote) {
        this.spanish = spanish;
        this.english = english;
        this.spanishNote = spanishNote;
        this.englishNote = englishNote;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    public String note(boolean useSpanish) {
        return useSpanish ? this.spanishNote : this.englishNote;
    }

    public PracticeDrill next() {
        PracticeDrill[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static PracticeDrill parse(String value) {
        for (PracticeDrill drill : values()) {
            if (drill.name().equalsIgnoreCase(value)) {
                return drill;
            }
        }
        return NONE;
    }
}
