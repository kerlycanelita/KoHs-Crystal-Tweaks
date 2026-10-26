package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/**
 * Shape the death flash takes. Purely a drawing choice: the light, its colour, its duration and the
 * ground spill are the same whichever one is selected, and none of them touch an entity or a packet.
 * Each shape has settings of its own, kept apart from every other shape's.
 */
public enum CrystalFlashStyle {
    /** The original burst: stacked discs with rotating facet rays. */
    EXPLOSION("Explosión", "Explosion",
            "El estallido original: discos de luz apilados y rayos que giran.",
            "The original burst: stacked discs of light and turning rays."),
    SKULL("Calavera", "Skull",
            "Una calavera de luz.",
            "A skull of light."),
    /** The player's own head, drawn from their skin as a pale apparition. */
    MY_HEAD("Mi cabeza", "My head",
            "Tu propia cabeza, sacada de tu skin, como una aparición pálida.",
            "Your own head, from your skin, as a pale apparition."),
    /** Bolts thrown from the blast toward the players around it that the player can see. */
    LIGHTNING("Rayos", "Lightning",
            "Rayos que salen del estallido hacia los jugadores que ves; un anillo de ellos si no ves a nadie.",
            "Bolts thrown from the blast toward the players you can see; a ring of them when you see nobody."),
    HEART("Corazón", "Heart",
            "Un corazón de luz.",
            "A heart of light."),
    STAR("Estrella", "Star",
            "Una estrella que gira mientras se apaga, con otra ardiendo dentro.",
            "A star that turns as it fades, with another burning inside it."),
    /** A ring that expands as the flash fades. */
    SHOCKWAVE("Onda expansiva", "Shockwave",
            "Un anillo que se abre mientras el destello se apaga, y otro detrás.",
            "A ring that spreads as the flash fades, another behind it."),
    /** Arms spiralling out of the blast. */
    VORTEX("Vórtice", "Vortex",
            "Brazos de luz que salen en espiral del estallido y giran.",
            "Arms of light spiralling out of the blast, turning."),
    CROWN("Corona", "Crown",
            "Una corona de luz con sus joyas.",
            "A crown of light with its jewels."),
    CRESCENT("Luna", "Crescent",
            "Una luna creciente con dos estrellas.",
            "A crescent moon with two stars."),
    SNOWFLAKE("Copo de nieve", "Snowflake",
            "Un copo de nieve de seis brazos que gira despacio.",
            "A six-armed snowflake turning slowly."),
    FLOWER("Flor", "Flower",
            "Una flor de pétalos de luz alrededor de un corazón ardiente.",
            "A flower of petals of light round a burning heart."),
    GEM("Gema", "Gem",
            "Una gema tallada, con sus facetas.",
            "A cut gem, with its facets."),
    SWORDS("Espadas", "Swords",
            "Dos espadas cruzadas por la hoja.",
            "Two swords crossed at their blades.");

    private final String spanish;
    private final String english;
    private final String spanishDescription;
    private final String englishDescription;

    CrystalFlashStyle(String spanish, String english, String spanishDescription, String englishDescription) {
        this.spanish = spanish;
        this.english = english;
        this.spanishDescription = spanishDescription;
        this.englishDescription = englishDescription;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    public String description(boolean useSpanish) {
        return useSpanish ? this.spanishDescription : this.englishDescription;
    }

    /** True for the styles the size slider applies to; the burst keeps its own proportions. */
    public boolean scalable() {
        return this != EXPLOSION;
    }

    /** The shape's own settings, kept apart from every other shape's. */
    public StyleSetting[] settings() {
        return switch (this) {
            case EXPLOSION -> Settings.EXPLOSION;
            case SKULL -> Settings.SKULL;
            case MY_HEAD -> Settings.MY_HEAD;
            case LIGHTNING -> Settings.LIGHTNING;
            case HEART -> Settings.HEART;
            case STAR -> Settings.STAR;
            case SHOCKWAVE -> Settings.SHOCKWAVE;
            case VORTEX -> Settings.VORTEX;
            case CROWN -> Settings.CROWN;
            case CRESCENT -> Settings.CRESCENT;
            case SNOWFLAKE -> Settings.SNOWFLAKE;
            case FLOWER -> Settings.FLOWER;
            case GEM -> Settings.GEM;
            case SWORDS -> Settings.SWORDS;
        };
    }

    /** Every shape's own settings back where they start. */
    public static void resetSettings() {
        for (CrystalFlashStyle style : values()) {
            for (StyleSetting setting : style.settings()) {
                setting.set(setting.fallback());
            }
        }
    }

    public CrystalFlashStyle next() {
        CrystalFlashStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public CrystalFlashStyle previous() {
        CrystalFlashStyle[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static CrystalFlashStyle parse(String value, CrystalFlashStyle fallback) {
        if (value == null) {
            return fallback;
        }
        String wanted = value.trim();
        // 2.3.0 shipped a Steve head briefly; anyone who picked it gets their own head instead.
        if (wanted.equalsIgnoreCase("STEVE")) {
            return MY_HEAD;
        }
        for (CrystalFlashStyle style : values()) {
            if (style.name().equalsIgnoreCase(wanted)) {
                return style;
            }
        }
        return fallback;
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Every shape's own settings, each named where the renderer reads it. They start where the shapes were made. */
    public static final class Settings {
        public static final StyleSetting RAYS = new StyleSetting("rays", "Rays", "Rayos",
                "How many rays the burst throws out.",
                "Cuántos rayos lanza el estallido.", 0, 32, 16, StyleSetting.Unit.COUNT);
        public static final StyleSetting RAY_LENGTH = new StyleSetting("length", "Ray length", "Largo de los rayos",
                "How far the rays reach.",
                "Hasta dónde llegan los rayos.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting EYES = new StyleSetting("eyes", "Eyes", "Ojos",
                "How brightly its eyes burn. At 0% they are hollow.",
                "Cuánto arden sus ojos. Con 0% quedan huecos.", 0, 200, 0, StyleSetting.Unit.PERCENT);
        public static final StyleSetting JAW = new StyleSetting("jaw", "Jaw", "Mandíbula",
                "How far its jaw drops as the flash fades. At 0% it stays shut.",
                "Cuánto se le abre la mandíbula mientras se apaga. Con 0% no se mueve.", 0, 100, 0, StyleSetting.Unit.PERCENT);
        public static final StyleSetting PALLOR = new StyleSetting("pallor", "Pallor", "Palidez",
                "How pale the white veil over the face makes it.",
                "Cuánto la empalidece el velo blanco sobre la cara.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting HAT = new StyleSetting("hat", "Outer layer", "Capa exterior",
                "How much the outer layer of your skin shows over the face.",
                "Cuánto se ve la capa exterior de tu skin sobre la cara.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting BOLTS = new StyleSetting("bolts", "Bolts", "Rayos",
                "How many bolts it throws when there is nobody in sight to point at.",
                "Cuántos rayos lanza cuando no ves a nadie hacia quien apuntar.", 3, 12, 6, StyleSetting.Unit.COUNT);
        public static final StyleSetting ZIGZAG = new StyleSetting("zigzag", "Zigzag", "Zigzag",
                "How sharply each bolt zigzags. At 0% they are straight.",
                "Cuánto zigzaguea cada rayo. Con 0% son rectos.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting BEAT = new StyleSetting("beat", "Beat", "Latido",
                "How much the heart beats as it fades. At 0% it holds still.",
                "Cuánto late el corazón mientras se apaga. Con 0% se queda quieto.", 0, 100, 0, StyleSetting.Unit.PERCENT);
        public static final StyleSetting HEART_SHINE = new StyleSetting("shine", "Shine", "Brillo",
                "How hot its middle burns.",
                "Cuánto arde su centro.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting POINTS = new StyleSetting("points", "Points", "Puntas",
                "How many points the star has.",
                "Cuántas puntas tiene la estrella.", 4, 9, 5, StyleSetting.Unit.COUNT);
        public static final StyleSetting STAR_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How far the star turns as it fades. At 0% it holds still.",
                "Cuánto gira la estrella mientras se apaga. Con 0% se queda quieta.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting REACH = new StyleSetting("reach", "Reach", "Alcance",
                "How far the ring runs out.",
                "Hasta dónde se abre el anillo.", 50, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting THICKNESS = new StyleSetting("thickness", "Thickness", "Grosor",
                "How thick the rings are.",
                "Lo gruesos que son los anillos.", 50, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting ARMS = new StyleSetting("arms", "Arms", "Brazos",
                "How many arms spiral out.",
                "Cuántos brazos salen en espiral.", 2, 6, 3, StyleSetting.Unit.COUNT);
        public static final StyleSetting VORTEX_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How far the arms turn as it fades. At 0% they hold still.",
                "Cuánto giran los brazos mientras se apaga. Con 0% se quedan quietos.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting TWIST = new StyleSetting("twist", "Twist", "Torsión",
                "How tightly the arms curl.",
                "Lo cerrado de la espiral.", 50, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting JEWELS = new StyleSetting("jewels", "Jewels", "Joyas",
                "How bright its jewels burn.",
                "Cuánto arden sus joyas.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting RISE = new StyleSetting("rise", "Rise", "Ascenso",
                "How far the crown rises as it fades. At 0% it stays where it was.",
                "Cuánto sube la corona mientras se apaga. Con 0% se queda donde estaba.", 0, 100, 0, StyleSetting.Unit.PERCENT);
        public static final StyleSetting TILT = new StyleSetting("tilt", "Tilt", "Inclinación",
                "How far the moon leans.",
                "Cuánto se inclina la luna.", -45, 45, 0, StyleSetting.Unit.DEGREES);
        public static final StyleSetting MOON_STARS = new StyleSetting("stars", "Stars", "Estrellas",
                "How bright its two stars are. At 0% the moon is alone.",
                "Cuánto brillan sus dos estrellas. Con 0% la luna queda sola.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting BRANCHES = new StyleSetting("branches", "Branches", "Ramas",
                "How many pairs of branches each arm has.",
                "Cuántos pares de ramas tiene cada brazo.", 0, 3, 2, StyleSetting.Unit.COUNT);
        public static final StyleSetting FLAKE_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How far the snowflake turns as it fades. At 0% it holds still.",
                "Cuánto gira el copo mientras se apaga. Con 0% se queda quieto.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting PETALS = new StyleSetting("petals", "Petals", "Pétalos",
                "How many petals the flower has.",
                "Cuántos pétalos tiene la flor.", 4, 10, 6, StyleSetting.Unit.COUNT);
        public static final StyleSetting FLOWER_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How far the flower turns as it fades. At 0% it holds still.",
                "Cuánto gira la flor mientras se apaga. Con 0% se queda quieta.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting SPARKLE = new StyleSetting("sparkle", "Sparkle", "Destello",
                "How hot its heart and its point burn.",
                "Cuánto arden su corazón y su punta.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting FACETS = new StyleSetting("facets", "Facets", "Facetas",
                "How much its dim facets show.",
                "Cuánto se ven sus facetas tenues.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting CROSS = new StyleSetting("cross", "Cross", "Cruce",
                "How far apart the swords lean.",
                "Cuánto se separan las espadas al cruzarse.", 10, 70, 40, StyleSetting.Unit.DEGREES);
        public static final StyleSetting EDGE = new StyleSetting("edge", "Edge", "Filo",
                "How bright their edges and hilts burn.",
                "Cuánto arden sus filos y empuñaduras.", 0, 200, 100, StyleSetting.Unit.PERCENT);

        static final StyleSetting[] EXPLOSION = {RAYS, RAY_LENGTH};
        static final StyleSetting[] SKULL = {EYES, JAW};
        static final StyleSetting[] MY_HEAD = {PALLOR, HAT};
        static final StyleSetting[] LIGHTNING = {BOLTS, ZIGZAG};
        static final StyleSetting[] HEART = {BEAT, HEART_SHINE};
        static final StyleSetting[] STAR = {POINTS, STAR_SPIN};
        static final StyleSetting[] SHOCKWAVE = {REACH, THICKNESS};
        static final StyleSetting[] VORTEX = {ARMS, VORTEX_SPIN, TWIST};
        static final StyleSetting[] CROWN = {JEWELS, RISE};
        static final StyleSetting[] CRESCENT = {TILT, MOON_STARS};
        static final StyleSetting[] SNOWFLAKE = {BRANCHES, FLAKE_SPIN};
        static final StyleSetting[] FLOWER = {PETALS, FLOWER_SPIN};
        static final StyleSetting[] GEM = {SPARKLE, FACETS};
        static final StyleSetting[] SWORDS = {CROSS, EDGE};

        private Settings() {
        }
    }
}
