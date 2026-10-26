package com.zymekoh.crystaltweaks.client;

import java.util.Locale;

/** How the crystal's layers glow. The halo behind the crystal is apart: Old KoHs Crystal Glow. */
public enum CrystalGlowStyle {
    /** A soft light round every lit pixel, laid over the scene: it keeps its colour and never burns out. */
    LIGHT("Light", "Luz",
            "A soft light round every lit pixel, laid over the scene: it keeps its colour and never burns out.",
            "Una luz suave alrededor de cada píxel encendido, sobre la escena: mantiene su color y nunca se quema."),
    /** The layers drawn again as added light, with an aura: brighter and more saturated. */
    LAYERS("Layers", "Capas",
            "The layers drawn again as added light, with an aura: brighter and more saturated.",
            "Las capas dibujadas otra vez como luz añadida, con un aura: más brillante y saturado."),
    /** The Light style beating like a heart, a ring going out with each beat. */
    HEARTBEAT("Heartbeat", "Latido",
            "Light that beats like a heart, twice and a rest, and sends a ring out with each beat.",
            "Una luz que late como un corazón, dos golpes y una pausa, y suelta un anillo con cada latido."),
    /** The Light style with curtains of light waving round the crystal. */
    AURORA("Aurora", "Aurora",
            "Curtains of light wave round the crystal and run through its colours.",
            "Cortinas de luz ondean alrededor del cristal y recorren sus colores."),
    /** The Light style with sparks on tilted orbits, each with its trail. */
    ORBIT("Orbit", "Órbita",
            "Sparks of light orbit the crystal on tilted paths, each with its trail.",
            "Chispas de luz giran alrededor del cristal en órbitas inclinadas, cada una con su estela."),
    /** The Layers style with flames rising behind the crystal. */
    FLAMES("Flames", "Llamas",
            "Added-light layers, with flames in the glow's colour rising behind the crystal.",
            "Capas de luz añadida, con llamas del color del brillo que suben por detrás del cristal."),
    /** The Light style over a sigil of light turning on the ground. */
    SIGIL("Sigil", "Sello",
            "A sigil of light turns on the ground under the crystal, pulsing.",
            "Un sello de luz gira en el suelo bajo el cristal, latiendo.");

    private final String english;
    private final String spanish;
    private final String englishDescription;
    private final String spanishDescription;

    CrystalGlowStyle(String english, String spanish, String englishDescription, String spanishDescription) {
        this.english = english;
        this.spanish = spanish;
        this.englishDescription = englishDescription;
        this.spanishDescription = spanishDescription;
    }

    public String label(boolean inSpanish) {
        return inSpanish ? this.spanish : this.english;
    }

    public String description(boolean inSpanish) {
        return inSpanish ? this.spanishDescription : this.englishDescription;
    }

    /** Whether the layers are drawn again as added light, as in Layers, rather than wearing soft sheets. */
    public boolean layered() {
        return this == LAYERS || this == FLAMES;
    }

    /** Whether the style has light of its own round the crystal, moving with the crystal's clock. */
    public boolean animated() {
        return this != LIGHT && this != LAYERS;
    }

    /** The style's own settings, kept apart from every other style's: each keeps its own when another is chosen. */
    public StyleSetting[] settings() {
        return switch (this) {
            case LIGHT -> Settings.LIGHT;
            case LAYERS -> Settings.LAYERS;
            case HEARTBEAT -> Settings.HEARTBEAT;
            case AURORA -> Settings.AURORA;
            case ORBIT -> Settings.ORBIT;
            case FLAMES -> Settings.FLAMES;
            case SIGIL -> Settings.SIGIL;
        };
    }

    /** Every style's own settings back where they start. */
    public static void resetSettings() {
        for (CrystalGlowStyle style : values()) {
            for (StyleSetting setting : style.settings()) {
                setting.set(setting.fallback());
            }
        }
    }

    /** Every style's own settings, each named where the renderer reads it. They start where the styles were made. */
    public static final class Settings {
        public static final StyleSetting HALO = new StyleSetting("halo", "Core halo", "Halo del núcleo",
                "How far the core's round light reaches.",
                "Hasta dónde llega la luz redonda del núcleo.", 50, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting BREATH = new StyleSetting("breath", "Breath", "Respiración",
                "The light rises and falls slowly, like breathing. At 0% it holds steady.",
                "La luz sube y baja despacio, como al respirar. Con 0% se queda fija.", 0, 100, 0, StyleSetting.Unit.PERCENT);
        public static final StyleSetting AURA_REACH = new StyleSetting("auraReach", "Aura reach", "Alcance del aura",
                "How far the aura spreads round the crystal.",
                "Hasta dónde se abre el aura alrededor del cristal.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting AURA_LIGHT = new StyleSetting("auraLight", "Aura light", "Luz del aura",
                "How bright the aura is. At 0% the layers glow alone.",
                "Cuánto brilla el aura. Con 0% brillan solo las capas.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting RHYTHM = new StyleSetting("rhythm", "Rhythm", "Ritmo",
                "Heartbeats a minute: each one is two beats and a rest.",
                "Latidos por minuto: cada uno son dos golpes y una pausa.", 30, 120, 50, StyleSetting.Unit.BEATS);
        public static final StyleSetting THROB = new StyleSetting("throb", "Throb", "Fuerza",
                "How much the light swells with each beat. At 0% it holds steady.",
                "Cuánto se hincha la luz con cada golpe. Con 0% se queda fija.", 0, 200, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting RINGS = new StyleSetting("rings", "Rings", "Anillos",
                "How far each beat's ring goes out. At 0% there are no rings.",
                "Hasta dónde llega el anillo de cada golpe. Con 0% no hay anillos.", 0, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting AURORA_SPEED = new StyleSetting("speed", "Speed", "Velocidad",
                "How fast the curtains wave and run through the colours.",
                "Lo rápido que ondean las cortinas y recorren los colores.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting AURORA_HEIGHT = new StyleSetting("height", "Height", "Altura",
                "How tall the curtains rise.",
                "Lo alto que suben las cortinas.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting ORBITS = new StyleSetting("orbits", "Orbits", "Órbitas",
                "How many tilted paths the sparks fly on.",
                "En cuántas órbitas inclinadas vuelan las chispas.", 1, 5, 3, StyleSetting.Unit.COUNT);
        public static final StyleSetting ORBIT_SPEED = new StyleSetting("speed", "Speed", "Velocidad",
                "How fast the sparks go round.",
                "Lo rápido que giran las chispas.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting TRAIL = new StyleSetting("trail", "Trail", "Estela",
                "How many fading sparks follow each one.",
                "Cuántas chispas, cada vez más tenues, siguen a cada una.", 0, 8, 4, StyleSetting.Unit.COUNT);
        public static final StyleSetting FLAME_HEIGHT = new StyleSetting("height", "Height", "Altura",
                "How high the flames climb.",
                "Lo alto que suben las llamas.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting FLAME_SPEED = new StyleSetting("speed", "Speed", "Velocidad",
                "How fast the flames flicker.",
                "Lo rápido que titilan las llamas.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting TONGUES = new StyleSetting("tongues", "Tongues", "Lenguas",
                "How many tongues of flame rise.",
                "Cuántas lenguas de fuego suben.", 3, 13, 9, StyleSetting.Unit.COUNT);
        public static final StyleSetting SIGIL_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How fast the sigil turns. At 0% it holds still.",
                "Lo rápido que gira el sello. Con 0% se queda quieto.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting SIGIL_SIZE = new StyleSetting("size", "Size", "Tamaño",
                "How wide the sigil is on the ground.",
                "Lo ancho que es el sello en el suelo.", 60, 140, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting POINTS = new StyleSetting("points", "Points", "Puntas",
                "How many points its star has.",
                "Cuántas puntas tiene su estrella.", 5, 9, 5, StyleSetting.Unit.COUNT);

        static final StyleSetting[] LIGHT = {HALO, BREATH};
        static final StyleSetting[] LAYERS = {AURA_REACH, AURA_LIGHT};
        static final StyleSetting[] HEARTBEAT = {RHYTHM, THROB, RINGS};
        static final StyleSetting[] AURORA = {AURORA_SPEED, AURORA_HEIGHT};
        static final StyleSetting[] ORBIT = {ORBITS, ORBIT_SPEED, TRAIL};
        static final StyleSetting[] FLAMES = {FLAME_HEIGHT, FLAME_SPEED, TONGUES};
        static final StyleSetting[] SIGIL = {SIGIL_SPIN, SIGIL_SIZE, POINTS};

        private Settings() {
        }
    }

    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public CrystalGlowStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrystalGlowStyle parse(String key, CrystalGlowStyle fallback) {
        for (CrystalGlowStyle style : values()) {
            if (style.storageKey().equalsIgnoreCase(key)) {
                return style;
            }
        }
        return fallback;
    }
}
