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
            "Un sello de luz gira en el suelo bajo el cristal, latiendo."),
    /** The Layers style with bolts of light striking out of the crystal. */
    STORM("Storm", "Tormenta",
            "Added-light layers, and bolts of light that crackle out of the crystal, strike and fade.",
            "Capas de luz añadida, y rayos de luz que chisporrotean desde el cristal, golpean y se apagan."),
    /** The Light style with two wings of light beating behind the crystal. */
    WINGS("Wings", "Alas",
            "Two wings of light unfold behind the crystal and beat slowly, feather by feather.",
            "Dos alas de luz se abren detrás del cristal y baten despacio, pluma a pluma."),
    /** The Light style under a crown of thorns of light, turning above the crystal. */
    CROWN("Crown", "Corona",
            "A crown of thorns of light floats above the crystal, turning and pulsing.",
            "Una corona de espinas de luz flota sobre el cristal, girando y latiendo."),
    /** The Layers style in a spiral of the void drawing its light in. */
    VORTEX("Vortex", "Vórtice",
            "Added-light layers in a spiral of the void that turns round the crystal and draws the light in.",
            "Capas de luz añadida en una espiral del vacío que gira alrededor del cristal y absorbe la luz."),
    /** The Light style with souls of light rising round the crystal. */
    SOULS("Souls", "Almas",
            "Souls of light rise round the crystal in a slow spiral and fade away.",
            "Almas de luz suben alrededor del cristal en una espiral lenta y se desvanecen.");

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
        return this == LAYERS || this == FLAMES || this == STORM || this == VORTEX;
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
            case STORM -> Settings.STORM;
            case WINGS -> Settings.WINGS;
            case CROWN -> Settings.CROWN;
            case VORTEX -> Settings.VORTEX;
            case SOULS -> Settings.SOULS;
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

        public static final StyleSetting BOLTS = new StyleSetting("bolts", "Bolts", "Rayos",
                "How many bolts strike at once.",
                "Cuántos rayos golpean a la vez.", 1, 8, 4, StyleSetting.Unit.COUNT);
        public static final StyleSetting STRIKES = new StyleSetting("strikes", "Strikes", "Descargas",
                "How often the bolts strike.",
                "Lo seguido que golpean los rayos.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting BOLT_REACH = new StyleSetting("reach", "Reach", "Alcance",
                "How far the bolts reach from the crystal.",
                "Hasta dónde llegan los rayos desde el cristal.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting SPAN = new StyleSetting("span", "Span", "Envergadura",
                "How wide the wings open.",
                "Lo que se abren las alas.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting WING_BEAT = new StyleSetting("beat", "Beat", "Aleteo",
                "How fast the wings beat. At 0% they hold still, open.",
                "Lo rápido que baten las alas. Con 0% se quedan quietas, abiertas.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting FEATHERS = new StyleSetting("feathers", "Feathers", "Plumas",
                "How many feathers each wing has.",
                "Cuántas plumas tiene cada ala.", 3, 9, 6, StyleSetting.Unit.COUNT);
        public static final StyleSetting THORNS = new StyleSetting("thorns", "Thorns", "Espinas",
                "How many thorns the crown has.",
                "Cuántas espinas tiene la corona.", 5, 13, 8, StyleSetting.Unit.COUNT);
        public static final StyleSetting CROWN_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How fast the crown turns. At 0% it holds still.",
                "Lo rápido que gira la corona. Con 0% se queda quieta.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting CROWN_HEIGHT = new StyleSetting("height", "Height", "Altura",
                "How high above the crystal the crown floats.",
                "Lo alto que flota la corona sobre el cristal.", 50, 160, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting ARMS = new StyleSetting("arms", "Arms", "Brazos",
                "How many arms the spiral has.",
                "Cuántos brazos tiene la espiral.", 2, 6, 3, StyleSetting.Unit.COUNT);
        public static final StyleSetting VORTEX_SPIN = new StyleSetting("spin", "Spin", "Giro",
                "How fast the spiral turns.",
                "Lo rápido que gira la espiral.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting PULL = new StyleSetting("pull", "Pull", "Absorción",
                "How fast the motes of light fall into the crystal. At 0% there are none.",
                "Lo rápido que caen al cristal las motas de luz. Con 0% no hay.", 0, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting SOUL_COUNT = new StyleSetting("souls", "Souls", "Almas",
                "How many souls rise at once.",
                "Cuántas almas suben a la vez.", 3, 12, 6, StyleSetting.Unit.COUNT);
        public static final StyleSetting RISE = new StyleSetting("rise", "Rise", "Subida",
                "How fast the souls rise.",
                "Lo rápido que suben las almas.", 25, 300, 100, StyleSetting.Unit.PERCENT);
        public static final StyleSetting SWAY = new StyleSetting("sway", "Sway", "Vaivén",
                "How much the souls drift from side to side. At 0% they rise straight.",
                "Cuánto se mecen las almas de lado a lado. Con 0% suben rectas.", 0, 200, 100, StyleSetting.Unit.PERCENT);

        static final StyleSetting[] LIGHT = {HALO, BREATH};
        static final StyleSetting[] LAYERS = {AURA_REACH, AURA_LIGHT};
        static final StyleSetting[] HEARTBEAT = {RHYTHM, THROB, RINGS};
        static final StyleSetting[] AURORA = {AURORA_SPEED, AURORA_HEIGHT};
        static final StyleSetting[] ORBIT = {ORBITS, ORBIT_SPEED, TRAIL};
        static final StyleSetting[] FLAMES = {FLAME_HEIGHT, FLAME_SPEED, TONGUES};
        static final StyleSetting[] SIGIL = {SIGIL_SPIN, SIGIL_SIZE, POINTS};
        static final StyleSetting[] STORM = {BOLTS, STRIKES, BOLT_REACH};
        static final StyleSetting[] WINGS = {SPAN, WING_BEAT, FEATHERS};
        static final StyleSetting[] CROWN = {THORNS, CROWN_SPIN, CROWN_HEIGHT};
        static final StyleSetting[] VORTEX = {ARMS, VORTEX_SPIN, PULL};
        static final StyleSetting[] SOULS = {SOUL_COUNT, RISE, SWAY};

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
