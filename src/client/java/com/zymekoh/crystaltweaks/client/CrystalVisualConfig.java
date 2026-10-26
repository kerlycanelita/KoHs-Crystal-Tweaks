package com.zymekoh.crystaltweaks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CrystalVisualConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    /** A fresh install's crystal: purple frame, lighter purple inner frame, lavender core. */
    private static final int DEFAULT_OUTER = 0xFF8702F2;
    private static final int DEFAULT_INNER = 0xFFB45CFF;
    private static final int DEFAULT_CORE = 0xFFD08CFF;
    private static final Object LOCK = new Object();
    private static volatile boolean customSoundEnabled;
    private static volatile String customSoundFileName = "";
    private static volatile float soundVolume = 1.0F;
    private static volatile float soundSpeed = 1.0F;
    private static volatile boolean safeCrystal;
    private static volatile boolean converterNoticeDismissed;
    private static volatile boolean scrollHintDone;
    private static volatile boolean carouselHintDone;
    private static volatile boolean obsidianDebounce;
    /** The client-side crystal: a hit crystal hides and explodes on screen at once; packets stay Vanilla's. */
    private static volatile boolean clientCrystal;
    private static volatile boolean instantExplosion = true;
    private static volatile boolean ghostCrystals;
    private static volatile boolean refusedHitCue = true;
    /** Placement at the server's pace: a click on the crystal just broken is sent when the server removes it. */
    private static volatile boolean fastPlacement;
    /** In a real fight: why a placement did not happen, the crystal round trip, and a recap when you die. */
    private static volatile boolean placementDiagnostic;
    private static volatile boolean latencyMeter;
    private static volatile boolean deathRecap;
    private static volatile int obsidianDebounceMillis;
    private static volatile boolean herziumIntegration = true;
    private static volatile boolean benchmarkDevMode;
    private static volatile String practiceArmor = "NETHERITE";
    private static volatile int practiceBlastPieces = 0b0100;
    private static volatile boolean practiceBot = true;
    private static volatile String practiceBotDifficulty = "NORMAL";
    private static volatile String practiceBotStyle = "SMART";
    private static volatile int practiceKnockback = 1;
    private static volatile String practicePreset = "standard";
    private static volatile String practiceWorld = "FLAT";
    private static volatile String practiceBiome = "plains";
    private static volatile boolean practiceServerWindow = true;
    private static volatile boolean practiceServerView = true;
    private static volatile boolean practiceServerAlerts = true;
    /** Back to the parked server by itself when its match starts. */
    private static volatile boolean practiceAutoReturn;
    /** The practice world's ping in milliseconds; -1 copies the parked server's. */
    private static volatile int practicePing;
    private static volatile String practiceDrill = "NONE";
    private static volatile boolean practiceRoundReport = true;
    private static volatile boolean practiceRoundMap = true;
    private static volatile boolean practiceDamageMap;
    /** Kits the player rearranged and saved, by preset id, in {@link com.zymekoh.crystaltweaks.practice.KitLayout}'s text form. */
    private static final Map<String, String> practiceKits = new LinkedHashMap<>();
    private static volatile CrystalFlashStyle flashStyle = CrystalFlashStyle.CROWN;
    /** The glow as it was before it moved into the crystal's layers: a halo behind it. */
    private static volatile boolean oldGlow;
    private static volatile CrystalGlowStyle glowStyle = CrystalGlowStyle.LIGHT;
    private static volatile CrystalGlowQuality glowQuality = CrystalGlowQuality.BALANCED;
    private static volatile boolean loaded;
    private static final CrystalAppearance playerVisuals = defaultPlayerVisuals();
    private static CrystalAppearance enemyVisuals = defaultEnemyVisuals();
    private static boolean enemyCustomEnabled = true;

    /** Your crystals out of the box: purple all through, layers and glow. */
    private static CrystalAppearance defaultPlayerVisuals() {
        CrystalAppearance player = new CrystalAppearance();
        player.outerColor = DEFAULT_OUTER;
        player.innerColor = DEFAULT_INNER;
        player.coreColor = DEFAULT_CORE;
        return player;
    }

    /** Enemy crystals glow red out of the box; yours stay the crystal purple. */
    private static CrystalAppearance defaultEnemyVisuals() {
        CrystalAppearance enemy = new CrystalAppearance();
        enemy.glowColor = 0xFFFF3B3B;
        return enemy;
    }

    /** A fresh profile with the shipped defaults, for the settings screen's reset. */
    public static CrystalAppearance defaults(boolean enemy) {
        return enemy ? defaultEnemyVisuals() : defaultPlayerVisuals();
    }

    public static CrystalAppearance visuals(boolean enemy) {
        load();
        return enemy ? enemyVisuals : playerVisuals;
    }

    /**
     * The enemy profile is under repair: while this is set every crystal is drawn with the player's
     * own profile and the settings cannot open the enemy menu. The saved switch is left as it is.
     */
    public static final boolean ENEMY_PROFILE_UNDER_REPAIR = true;

    public static boolean enemyCustomEnabled() { load(); return !ENEMY_PROFILE_UNDER_REPAIR && enemyCustomEnabled; }
    public static void setEnemyCustomEnabled(boolean enabled) { load(); enemyCustomEnabled = enabled; }

    private CrystalVisualConfig() {
    }

    public static void load() {
        if (loaded) {
            return;
        }

        synchronized (LOCK) {
            if (loaded) {
                return;
            }

            Path path = configPath();
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject visuals = root.has("visuals") && root.get("visuals").isJsonObject()
                            ? root.getAsJsonObject("visuals")
                            : new JsonObject();
                    playerVisuals.outerColor = parseColor(visuals, "outerColor", DEFAULT_OUTER);
                    playerVisuals.innerColor = parseColor(visuals, "innerColor", DEFAULT_INNER);
                    playerVisuals.coreColor = parseColor(visuals, "coreColor", DEFAULT_CORE);
                    playerVisuals.rotationSpeedPercent = clamp(intValue(visuals, "rotationSpeedPercent", 100), 0, 300);
                    playerVisuals.floatingSpeedPercent = clamp(intValue(visuals, "floatingSpeedPercent", 100), 0, 300);
                    playerVisuals.sizePercent = clamp(intValue(visuals, "sizePercent", 100),
                            CrystalAppearance.MIN_SIZE, CrystalAppearance.MAX_SIZE);
                    playerVisuals.converterEnabled = booleanValue(visuals, "converterEnabled", false);
                    playerVisuals.converterTarget = stringValue(visuals, "converterTarget", "");

                    JsonObject sounds = section(root, "sounds");
                    customSoundEnabled = booleanValue(sounds, "enabled", false);
                    customSoundFileName = stringValue(sounds, "file", "");
                    // Anything above 100% never played louder, Vanilla clamps the gain at one; it only
                    // stretched how far away an explosion could be heard. See setSoundVolume.
                    soundVolume = clamp(floatValue(sounds, "volume", 1.0F), 0.0F, 1.0F);
                    soundSpeed = clamp(floatValue(sounds, "speed", 1.0F), 0.5F, 2.0F);

                    JsonObject gameplay = section(root, "gameplay");
                    safeCrystal = booleanValue(gameplay, "safeCrystal", false);
                    obsidianDebounce = booleanValue(gameplay, "obsidianDebounce", false);
                    clientCrystal = booleanValue(gameplay, "clientCrystal", false);
                    instantExplosion = booleanValue(gameplay, "instantExplosion", true);
                    ghostCrystals = booleanValue(gameplay, "ghostCrystals", false);
                    refusedHitCue = booleanValue(gameplay, "refusedHitCue", true);
                    fastPlacement = booleanValue(gameplay, "fastPlacement", false);
                    placementDiagnostic = booleanValue(gameplay, "placementDiagnostic", false);
                    latencyMeter = booleanValue(gameplay, "latencyMeter", false);
                    deathRecap = booleanValue(gameplay, "deathRecap", false);
                    obsidianDebounceMillis = clamp(intValue(gameplay, "obsidianDebounceMillis", 0), 0,
                            MAX_OBSIDIAN_DEBOUNCE_MILLIS);

                    JsonObject notices = section(root, "notices");
                    converterNoticeDismissed = booleanValue(notices, "converterNoticeDismissed", false);
                    scrollHintDone = booleanValue(notices, "scrollHintDone", false);
                    carouselHintDone = booleanValue(notices, "carouselHintDone", false);

                    JsonObject integrations = section(root, "integrations");
                    herziumIntegration = booleanValue(integrations, "herzium", true);

                    JsonObject benchmark = section(root, "benchmark");
                    benchmarkDevMode = booleanValue(benchmark, "devMode", false);

                    JsonObject practice = section(root, "practice");
                    practiceArmor = stringValue(practice, "armor", "NETHERITE");
                    practiceBlastPieces = intValue(practice, "blastPieces", 0b0100) & 0b1111;
                    practiceBot = booleanValue(practice, "bot", true);
                    practiceBotDifficulty = stringValue(practice, "botDifficulty", "NORMAL");
                    practiceBotStyle = stringValue(practice, "botStyle", "SMART");
                    practiceKnockback = clamp(intValue(practice, "knockback", 1), 1, 2);
                    practicePreset = stringValue(practice, "preset", "standard");
                    practiceWorld = stringValue(practice, "world", "FLAT");
                    practiceBiome = stringValue(practice, "biome", "plains");
                    practiceServerWindow = booleanValue(practice, "serverWindow", true);
                    practiceServerView = booleanValue(practice, "serverView", true);
                    practiceServerAlerts = booleanValue(practice, "serverAlerts", true);
                    practiceAutoReturn = booleanValue(practice, "autoReturn", false);
                    practicePing = clamp(intValue(practice, "ping", 0), -1, MAX_PRACTICE_PING);
                    practiceDrill = stringValue(practice, "drill", "NONE");
                    practiceRoundReport = booleanValue(practice, "roundReport", true);
                    practiceRoundMap = booleanValue(practice, "roundMap", true);
                    practiceDamageMap = booleanValue(practice, "damageMap", false);
                    practiceKits.clear();
                    JsonObject kits = section(practice, "kits");
                    for (String key : kits.keySet()) {
                        String layout = stringValue(kits, key, "");
                        if (!layout.isBlank()) {
                            practiceKits.put(key.toLowerCase(Locale.ROOT), layout);
                        }
                    }

                    JsonObject glow = section(root, "glow");
                    // Read first: migrating a profile needs to know which style its flash size meant.
                    flashStyle = CrystalFlashStyle.parse(
                            stringValue(glow, "flashStyle", ""), CrystalFlashStyle.CROWN);
                    playerVisuals.glowReflectionsPercent = clamp(intValue(glow, "reflectionsPercent", 55), 0, 300);
                    playerVisuals.glowPowerPercent = clamp(intValue(glow, "powerPercent", 55), 0, 300);
                    playerVisuals.customGlowColor = booleanValue(glow, "customColor", true);
                    playerVisuals.glowColor = parseColor(glow, "color", 0xFFC880FF);
                    playerVisuals.flashScalePercent = clamp(intValue(glow, "flashScalePercent", CrystalAppearance.DEFAULT_FLASH_SCALE),
                            CrystalAppearance.MIN_FLASH_SCALE, 300);
                    playerVisuals.glowEnabled = booleanValue(glow, "enabled", true);
                    playerVisuals.flashEnabled = booleanValue(glow, "flashEnabled", true);
                    playerVisuals.flashOpacityPercent = clamp(intValue(glow, "flashOpacityPercent",
                            CrystalAppearance.DEFAULT_FLASH_OPACITY), CrystalAppearance.MIN_FLASH_OPACITY, 100);
                    playerVisuals.flashDurationMillis = clamp(intValue(glow, "flashDurationMillis",
                            CrystalAppearance.DEFAULT_FLASH_DURATION), CrystalAppearance.MIN_FLASH_DURATION,
                            CrystalAppearance.MAX_FLASH_DURATION);
                    playerVisuals.motionBlurPercent = clamp(intValue(glow, "motionBlurPercent",
                            CrystalAppearance.DEFAULT_MOTION_BLUR), 0, 100);
                    oldGlow = booleanValue(glow, "oldStyle", false);
                    playerVisuals.coreGlowPercent = clamp(intValue(glow, "coreGlowPercent", 100), 0, 300);
                    glowStyle = CrystalGlowStyle.parse(stringValue(glow, "style", ""), CrystalGlowStyle.LIGHT);
                    JsonObject styles = section(glow, "styles");
                    for (CrystalGlowStyle style : CrystalGlowStyle.values()) {
                        JsonObject own = section(styles, style.storageKey());
                        for (StyleSetting setting : style.settings()) {
                            setting.set(intValue(own, setting.key(), setting.fallback()));
                        }
                    }
                    JsonObject shapes = section(glow, "flashStyles");
                    for (CrystalFlashStyle shape : CrystalFlashStyle.values()) {
                        JsonObject own = section(shapes, shape.storageKey());
                        for (StyleSetting setting : shape.settings()) {
                            setting.set(intValue(own, setting.key(), setting.fallback()));
                        }
                    }
                    glowQuality = CrystalGlowQuality.parse(stringValue(glow, "quality", ""), CrystalGlowQuality.BALANCED);
                    playerVisuals.migrateLegacyGlow(glow.has("enabled"), glow.has("flashOpacityPercent"),
                            flashStyle.scalable());

                    JsonObject enemy = section(root, "enemyVisuals");
                    enemyCustomEnabled = booleanValue(enemy, "enabled", true);
                    try {
                        CrystalAppearance stored = GSON.fromJson(enemy, CrystalAppearance.class);
                        if (stored.flashScalePercent <= 0) stored.flashScalePercent = CrystalAppearance.DEFAULT_FLASH_SCALE;
                        stored.migrateLegacyGlow(enemy.has("glowEnabled"), enemy.has("flashOpacityPercent"),
                                flashStyle.scalable());
                        enemyVisuals = stored.copy();
                    } catch (RuntimeException invalidEnemySettings) {
                        enemyVisuals = defaultEnemyVisuals();
                        CrystalTweaksClient.LOGGER.warn("Invalid enemy visual settings; resetting only that profile");
                    }
                } catch (Exception exception) {
                    CrystalTweaksClient.LOGGER.warn(
                            "Could not read crystal visual settings from {}; using neutral colors",
                            path,
                            exception);
                }
            }
            loaded = true;
        }
    }

    private static JsonObject section(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    public static void save() {
        load();
        synchronized (LOCK) {
            Path path = configPath();
            try {
                Files.createDirectories(path.getParent());
                JsonObject root = readExistingRoot(path);
                JsonObject visuals = root.has("visuals") && root.get("visuals").isJsonObject()
                        ? root.getAsJsonObject("visuals")
                        : new JsonObject();
                visuals.addProperty("outerColor", toHex(playerVisuals.outerColor));
                visuals.addProperty("innerColor", toHex(playerVisuals.innerColor));
                visuals.addProperty("coreColor", toHex(playerVisuals.coreColor));
                visuals.addProperty("rotationSpeedPercent", playerVisuals.rotationSpeedPercent);
                visuals.addProperty("floatingSpeedPercent", playerVisuals.floatingSpeedPercent);
                visuals.addProperty("sizePercent", playerVisuals.sizePercent);
                visuals.addProperty("converterEnabled", playerVisuals.converterEnabled);
                visuals.addProperty("converterTarget", playerVisuals.converterTarget);
                root.add("visuals", visuals);

                root.remove("tweaks");

                JsonObject gameplay = root.has("gameplay") && root.get("gameplay").isJsonObject()
                        ? root.getAsJsonObject("gameplay")
                        : new JsonObject();
                // 2.2.7 briefly stored an instant-break toggle here; that behaviour is core now.
                gameplay.remove("predictCrystalBreak");
                // The switch that forced the old optimizations off went with the optimizer rule.
                gameplay.remove("forceOffOptimizations");
                gameplay.addProperty("clientCrystal", clientCrystal);
                gameplay.addProperty("instantExplosion", instantExplosion);
                gameplay.addProperty("ghostCrystals", ghostCrystals);
                gameplay.addProperty("refusedHitCue", refusedHitCue);
                gameplay.addProperty("fastPlacement", fastPlacement);
                gameplay.addProperty("placementDiagnostic", placementDiagnostic);
                gameplay.addProperty("latencyMeter", latencyMeter);
                gameplay.addProperty("deathRecap", deathRecap);
                gameplay.addProperty("safeCrystal", safeCrystal);
                gameplay.addProperty("obsidianDebounce", obsidianDebounce);
                gameplay.addProperty("obsidianDebounceMillis", obsidianDebounceMillis);
                root.add("gameplay", gameplay);

                JsonObject integrations = section(root, "integrations");
                integrations.addProperty("herzium", herziumIntegration);
                root.add("integrations", integrations);

                JsonObject benchmark = section(root, "benchmark");
                benchmark.addProperty("devMode", benchmarkDevMode);
                root.add("benchmark", benchmark);

                JsonObject practice = section(root, "practice");
                practice.addProperty("armor", practiceArmor);
                practice.addProperty("blastPieces", practiceBlastPieces);
                practice.addProperty("bot", practiceBot);
                practice.addProperty("botDifficulty", practiceBotDifficulty);
                practice.addProperty("botStyle", practiceBotStyle);
                practice.addProperty("knockback", practiceKnockback);
                practice.addProperty("preset", practicePreset);
                practice.addProperty("world", practiceWorld);
                practice.addProperty("biome", practiceBiome);
                practice.addProperty("serverWindow", practiceServerWindow);
                practice.addProperty("serverView", practiceServerView);
                practice.addProperty("serverAlerts", practiceServerAlerts);
                practice.addProperty("autoReturn", practiceAutoReturn);
                practice.addProperty("ping", practicePing);
                practice.addProperty("drill", practiceDrill);
                practice.addProperty("roundReport", practiceRoundReport);
                practice.addProperty("roundMap", practiceRoundMap);
                practice.addProperty("damageMap", practiceDamageMap);
                JsonObject kits = new JsonObject();
                synchronized (practiceKits) {
                    for (Map.Entry<String, String> kit : practiceKits.entrySet()) {
                        kits.addProperty(kit.getKey(), kit.getValue());
                    }
                }
                practice.add("kits", kits);
                root.add("practice", practice);

                JsonObject notices = root.has("notices") && root.get("notices").isJsonObject()
                        ? root.getAsJsonObject("notices")
                        : new JsonObject();
                notices.remove("optimizerAdviceDismissed");
                notices.remove("optimizerWindowDismissed");
                notices.addProperty("converterNoticeDismissed", converterNoticeDismissed);
                notices.addProperty("scrollHintDone", scrollHintDone);
                notices.addProperty("carouselHintDone", carouselHintDone);
                root.add("notices", notices);

                JsonObject glow = root.has("glow") && root.get("glow").isJsonObject()
                        ? root.getAsJsonObject("glow")
                        : new JsonObject();
                glow.addProperty("reflectionsPercent", playerVisuals.glowReflectionsPercent);
                JsonObject enemy = GSON.toJsonTree(enemyVisuals.copy()).getAsJsonObject();
                enemy.addProperty("enabled", enemyCustomEnabled);
                root.add("enemyVisuals", enemy);
                glow.addProperty("enabled", playerVisuals.glowEnabled);
                glow.addProperty("powerPercent", playerVisuals.glowPowerPercent);
                glow.addProperty("customColor", playerVisuals.customGlowColor);
                glow.addProperty("color", toHex(playerVisuals.glowColor));
                glow.addProperty("flashEnabled", playerVisuals.flashEnabled);
                glow.addProperty("flashStyle", flashStyle.storageKey());
                glow.addProperty("flashScalePercent", playerVisuals.flashScalePercent);
                glow.addProperty("flashOpacityPercent", playerVisuals.flashOpacityPercent);
                glow.addProperty("flashDurationMillis", playerVisuals.flashDurationMillis);
                glow.addProperty("motionBlurPercent", playerVisuals.motionBlurPercent);
                glow.addProperty("oldStyle", oldGlow);
                glow.addProperty("coreGlowPercent", playerVisuals.coreGlowPercent);
                glow.addProperty("style", glowStyle.storageKey());
                JsonObject styles = new JsonObject();
                for (CrystalGlowStyle style : CrystalGlowStyle.values()) {
                    JsonObject own = new JsonObject();
                    for (StyleSetting setting : style.settings()) {
                        own.addProperty(setting.key(), setting.value());
                    }
                    styles.add(style.storageKey(), own);
                }
                glow.add("styles", styles);
                JsonObject shapes = new JsonObject();
                for (CrystalFlashStyle shape : CrystalFlashStyle.values()) {
                    JsonObject own = new JsonObject();
                    for (StyleSetting setting : shape.settings()) {
                        own.addProperty(setting.key(), setting.value());
                    }
                    shapes.add(shape.storageKey(), own);
                }
                glow.add("flashStyles", shapes);
                glow.addProperty("quality", glowQuality.storageKey());
                root.add("glow", glow);

                JsonObject sounds = root.has("sounds") && root.get("sounds").isJsonObject()
                        ? root.getAsJsonObject("sounds")
                        : new JsonObject();
                sounds.addProperty("enabled", customSoundEnabled);
                sounds.addProperty("file", customSoundFileName);
                sounds.addProperty("volume", soundVolume);
                sounds.addProperty("speed", soundSpeed);
                root.add("sounds", sounds);

                // Written beside the real file and moved over it, so a crash mid-write can never leave
                // half a JSON file that the next launch would read as "no settings at all".
                Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
                }
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception exception) {
                CrystalTweaksClient.LOGGER.warn("Could not save crystal visual settings to {}", path, exception);
            }
        }
    }

    public static int outerColor() {
        load();
        return playerVisuals.outerColor;
    }

    public static int innerColor() {
        load();
        return playerVisuals.innerColor;
    }

    public static int coreColor() {
        load();
        return playerVisuals.coreColor;
    }

    public static void setOuterColor(int color) {
        load();
        playerVisuals.outerColor = opaque(color);
    }

    public static void setInnerColor(int color) {
        load();
        playerVisuals.innerColor = opaque(color);
    }

    public static void setCoreColor(int color) {
        load();
        playerVisuals.coreColor = opaque(color);
    }

    public static void resetColors() {
        playerVisuals.outerColor = DEFAULT_OUTER;
        playerVisuals.innerColor = DEFAULT_INNER;
        playerVisuals.coreColor = DEFAULT_CORE;
    }

    public static int rotationSpeedPercent() {
        load();
        return playerVisuals.rotationSpeedPercent;
    }

    public static void setRotationSpeedPercent(int percent) {
        load();
        playerVisuals.rotationSpeedPercent = clamp(percent, 0, 300);
    }

    public static int floatingSpeedPercent() {
        load();
        return playerVisuals.floatingSpeedPercent;
    }

    public static void setFloatingSpeedPercent(int percent) {
        load();
        playerVisuals.floatingSpeedPercent = clamp(percent, 0, 300);
    }

    public static boolean customSoundEnabled() {
        load();
        return customSoundEnabled;
    }

    public static void setCustomSoundEnabled(boolean enabled) {
        load();
        customSoundEnabled = enabled;
    }

    public static String customSoundFileName() {
        load();
        return customSoundFileName;
    }

    public static void setCustomSoundFileName(String fileName) {
        load();
        customSoundFileName = fileName == null ? "" : fileName;
    }

    public static float soundVolume() {
        load();
        return soundVolume;
    }

    /**
     * Capped at 100%. Vanilla clamps a sound's gain at one, so a higher volume never played louder:
     * it only multiplied the distance at which an explosion could still be heard, up to twice
     * Vanilla's 64 blocks at the old 200% maximum.
     */
    public static void setSoundVolume(float volume) {
        load();
        soundVolume = clamp(volume, 0.0F, 1.0F);
    }

    public static float soundSpeed() {
        load();
        return soundSpeed;
    }

    public static void setSoundSpeed(float speed) {
        load();
        soundSpeed = clamp(speed, 0.5F, 2.0F);
    }

    /**
     * Safe Crystal: keep the obsidian under your crystals from being mined by a stray click. It sends
     * fewer actions than Vanilla, so a player on a server that forbids input filters can turn it off.
     */
    public static boolean safeCrystal() {
        load();
        return safeCrystal;
    }

    public static void setSafeCrystal(boolean enabled) {
        load();
        safeCrystal = enabled;
    }

    /** True once the player asked never to see Converter My Crystal's notice again. */
    public static boolean converterNoticeDismissed() {
        load();
        return converterNoticeDismissed;
    }

    public static void setConverterNoticeDismissed(boolean dismissed) {
        load();
        converterNoticeDismissed = dismissed;
    }

    /** True once the player has scrolled a side panel: the turning-wheel hint has done its job. */
    public static boolean scrollHintDone() {
        load();
        return scrollHintDone;
    }

    public static void setScrollHintDone(boolean done) {
        load();
        scrollHintDone = done;
    }

    /** True once the player has turned the tab carousel themselves. */
    public static boolean carouselHintDone() {
        load();
        return carouselHintDone;
    }

    public static void setCarouselHintDone(boolean done) {
        load();
        carouselHintDone = done;
    }

    /** Longest obsidian debounce the slider offers. */
    public static final int MAX_OBSIDIAN_DEBOUNCE_MILLIS = 10_000;


    /** Obsidian debounce: refuses a second obsidian placement too soon after the last one. */
    public static boolean obsidianDebounce() {
        load();
        return obsidianDebounce;
    }

    public static void setObsidianDebounce(boolean enabled) {
        load();
        obsidianDebounce = enabled;
    }

    /** The debounce window; 0 is Vanilla, which never refuses a placement. */
    public static int obsidianDebounceMillis() {
        load();
        return obsidianDebounceMillis;
    }

    public static void setObsidianDebounceMillis(int millis) {
        load();
        obsidianDebounceMillis = clamp(millis, 0, MAX_OBSIDIAN_DEBOUNCE_MILLIS);
    }

    /** The client-side crystal: a crystal you hit hides at once, its hit box and every packet left to Vanilla. */
    public static boolean clientCrystal() {
        load();
        return clientCrystal;
    }

    public static void setClientCrystal(boolean enabled) {
        load();
        clientCrystal = enabled;
    }

    /** The explosion of a crystal you hit, heard and seen at the hit instead of a round trip later. */
    public static boolean instantExplosion() {
        load();
        return instantExplosion;
    }

    public static void setInstantExplosion(boolean enabled) {
        load();
        instantExplosion = enabled;
    }

    /** A stand-in crystal drawn on your obsidian until the server's arrives. */
    public static boolean ghostCrystals() {
        load();
        return ghostCrystals;
    }

    public static void setGhostCrystals(boolean enabled) {
        load();
        ghostCrystals = enabled;
    }

    /** A low note when a crystal you hit comes back because the server kept it. */
    public static boolean refusedHitCue() {
        load();
        return refusedHitCue;
    }

    public static void setRefusedHitCue(boolean enabled) {
        load();
        refusedHitCue = enabled;
    }

    /** A click on the crystal you just broke is sent as a placement the moment the server removes it. */
    public static boolean fastPlacement() {
        load();
        return fastPlacement;
    }

    public static void setFastPlacement(boolean enabled) {
        load();
        fastPlacement = enabled;
    }

    /** Whether Crystal Tweaks shows and changes Herzium's hotbar order when Herzium is installed. */
    public static boolean herziumIntegration() {
        load();
        return herziumIntegration;
    }

    public static void setHerziumIntegration(boolean enabled) {
        load();
        herziumIntegration = enabled;
    }

    /** The benchmark explains itself for developers instead of for players. */
    public static boolean benchmarkDevMode() {
        load();
        return benchmarkDevMode;
    }

    public static void setBenchmarkDevMode(boolean enabled) {
        load();
        benchmarkDevMode = enabled;
    }

    /** Everything Crystal Practice was set to, as one value. */
    public static com.zymekoh.crystaltweaks.practice.PracticeSettings practice() {
        load();
        return com.zymekoh.crystaltweaks.practice.PracticeSettings.from(practiceArmor, practiceBlastPieces, practiceBot,
                practiceBotDifficulty, practiceBotStyle, practiceKnockback, practicePreset, practiceWorld, practiceBiome);
    }

    /** The kit a preset gives: the player's saved arrangement of it, or the preset as it ships. */
    public static com.zymekoh.crystaltweaks.practice.KitLayout practiceKit(com.zymekoh.crystaltweaks.practice.KitPreset preset) {
        load();
        String saved;
        synchronized (practiceKits) {
            saved = practiceKits.get(preset.id);
        }
        com.zymekoh.crystaltweaks.practice.KitLayout layout = com.zymekoh.crystaltweaks.practice.KitLayout.decode(saved);
        return layout != null ? layout : preset.defaultLayout();
    }

    /** True when the player saved their own arrangement of this preset. */
    public static boolean practiceKitCustomized(com.zymekoh.crystaltweaks.practice.KitPreset preset) {
        load();
        synchronized (practiceKits) {
            return practiceKits.containsKey(preset.id);
        }
    }

    /** Stores a rearranged kit; one identical to the shipped preset is forgotten instead. */
    public static void setPracticeKit(com.zymekoh.crystaltweaks.practice.KitPreset preset,
            com.zymekoh.crystaltweaks.practice.KitLayout layout) {
        load();
        synchronized (practiceKits) {
            if (layout == null || layout.isEmpty() || layout.equals(preset.defaultLayout())) {
                practiceKits.remove(preset.id);
            } else {
                practiceKits.put(preset.id, layout.encode());
            }
        }
    }

    public static void setPracticeBotStyle(String style) {
        load();
        practiceBotStyle = style == null ? "SMART" : style;
    }

    public static int practiceKnockback() {
        load();
        return practiceKnockback;
    }

    public static void setPracticeKnockback(int level) {
        load();
        practiceKnockback = clamp(level, 1, 2);
    }

    public static void setPracticePreset(String preset) {
        load();
        practicePreset = preset == null ? "standard" : preset;
    }

    public static void setPracticeWorld(String world) {
        load();
        practiceWorld = world == null ? "FLAT" : world;
    }

    public static void setPracticeBiome(String biome) {
        load();
        practiceBiome = biome == null ? "plains" : biome;
    }

    public static String practiceArmor() {
        load();
        return practiceArmor;
    }

    public static void setPracticeArmor(String armor) {
        load();
        practiceArmor = armor == null ? "NETHERITE" : armor;
    }

    /** Armour pieces that carry Blast Protection IV instead of Protection IV: head 1, chest 2, legs 4, feet 8. */
    public static int practiceBlastPieces() {
        load();
        return practiceBlastPieces;
    }

    public static void setPracticeBlastPieces(int mask) {
        load();
        practiceBlastPieces = mask & 0b1111;
    }

    public static boolean practiceBot() {
        load();
        return practiceBot;
    }

    /** The window in the top-left corner showing the server parked behind the practice world. */
    public static boolean practiceServerWindow() {
        load();
        return practiceServerWindow;
    }

    public static void setPracticeServerWindow(boolean enabled) {
        load();
        practiceServerWindow = enabled;
    }

    /** That window's live view of the server's world. */
    public static boolean practiceServerView() {
        load();
        return practiceServerView;
    }

    public static void setPracticeServerView(boolean enabled) {
        load();
        practiceServerView = enabled;
    }

    /** A chime and a flash when something happens on the server behind. */
    public static boolean practiceServerAlerts() {
        load();
        return practiceServerAlerts;
    }

    /** The most ping the practice world can be given, in milliseconds. */
    public static final int MAX_PRACTICE_PING = 400;

    public static boolean placementDiagnostic() {
        load();
        return placementDiagnostic;
    }

    public static void setPlacementDiagnostic(boolean enabled) {
        load();
        placementDiagnostic = enabled;
    }

    public static boolean latencyMeter() {
        load();
        return latencyMeter;
    }

    public static void setLatencyMeter(boolean enabled) {
        load();
        latencyMeter = enabled;
    }

    public static boolean deathRecap() {
        load();
        return deathRecap;
    }

    public static void setDeathRecap(boolean enabled) {
        load();
        deathRecap = enabled;
    }

    public static boolean practiceAutoReturn() {
        load();
        return practiceAutoReturn;
    }

    public static void setPracticeAutoReturn(boolean enabled) {
        load();
        practiceAutoReturn = enabled;
    }

    /** The practice world's ping in milliseconds, 0 for none; -1 copies the parked server's. */
    public static int practicePing() {
        load();
        return practicePing;
    }

    public static void setPracticePing(int millis) {
        load();
        practicePing = clamp(millis, -1, MAX_PRACTICE_PING);
    }

    public static String practiceDrill() {
        load();
        return practiceDrill;
    }

    public static void setPracticeDrill(String drill) {
        load();
        practiceDrill = drill == null ? "NONE" : drill;
    }

    public static boolean practiceRoundReport() {
        load();
        return practiceRoundReport;
    }

    public static void setPracticeRoundReport(boolean enabled) {
        load();
        practiceRoundReport = enabled;
    }

    public static boolean practiceRoundMap() {
        load();
        return practiceRoundMap;
    }

    public static void setPracticeRoundMap(boolean enabled) {
        load();
        practiceRoundMap = enabled;
    }

    public static boolean practiceDamageMap() {
        load();
        return practiceDamageMap;
    }

    public static void setPracticeDamageMap(boolean enabled) {
        load();
        practiceDamageMap = enabled;
    }

    public static void setPracticeServerAlerts(boolean enabled) {
        load();
        practiceServerAlerts = enabled;
    }

    public static void setPracticeBot(boolean enabled) {
        load();
        practiceBot = enabled;
    }

    public static String practiceBotDifficulty() {
        load();
        return practiceBotDifficulty;
    }

    public static void setPracticeBotDifficulty(String difficulty) {
        load();
        practiceBotDifficulty = difficulty == null ? "NORMAL" : difficulty;
    }

    /** 100 retains the original glow scale; values up to 300 amplify additive light only. */
    public static int glowPowerPercent() {
        load();
        return playerVisuals.glowPowerPercent;
    }

    public static void setGlowPowerPercent(int percent) {
        load();
        playerVisuals.glowPowerPercent = clamp(percent, 0, 300);
    }

    /**
     * When on, the whole crystal is drawn in {@link #glowColor()} and the per-layer colors are
     * ignored without deleting them. Disabling the override restores the selected layer colors.
     */
    public static boolean customGlowColor() {
        load();
        return playerVisuals.customGlowColor;
    }

    public static void setCustomGlowColor(boolean enabled) {
        load();
        playerVisuals.customGlowColor = enabled;
    }

    /**
     * Shape the flash of a destroyed crystal takes. Drawing only: the light, its colour and its
     * duration are unchanged, and no style reads or reveals anything the client was not already
     * rendering.
     */
    public static CrystalFlashStyle flashStyle() {
        load();
        return flashStyle;
    }

    public static void setFlashStyle(CrystalFlashStyle style) {
        load();
        flashStyle = style == null ? CrystalFlashStyle.CROWN : style;
    }

    /** Old KoHs Crystal Glow: the halo behind the crystal instead of light in its layers, for every profile. */
    public static boolean oldGlow() {
        load();
        return oldGlow;
    }

    public static void setOldGlow(boolean enabled) {
        load();
        oldGlow = enabled;
    }

    /** How the layers glow, for every profile. */
    public static CrystalGlowStyle glowStyle() {
        load();
        return glowStyle;
    }

    public static void setGlowStyle(CrystalGlowStyle style) {
        load();
        glowStyle = style == null ? CrystalGlowStyle.LIGHT : style;
    }

    /** How much the glow, the blur and the reflections draw, for every profile. */
    public static CrystalGlowQuality glowQuality() {
        load();
        return glowQuality;
    }

    public static void setGlowQuality(CrystalGlowQuality quality) {
        load();
        glowQuality = quality == null ? CrystalGlowQuality.BALANCED : quality;
    }

    public static int glowColor() {
        load();
        return playerVisuals.glowColor;
    }

    public static void setGlowColor(int color) {
        load();
        playerVisuals.glowColor = opaque(color);
    }

    public static Path soundsDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystal_tweaks").resolve("sounds");
    }

    public static Path customSoundPath() {
        String fileName = customSoundFileName();
        return fileName.isBlank() ? null : soundsDirectory().resolve(fileName);
    }

    public static int parseHex(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("Color must contain exactly six hexadecimal digits");
        }
        return 0xFF000000 | Integer.parseInt(normalized, 16);
    }

    public static String toHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static int parseColor(JsonObject object, String key, int fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return parseHex(object.get(key).getAsString());
        } catch (RuntimeException exception) {
            CrystalTweaksClient.LOGGER.warn("Invalid {} value in Crystal Tweaks config", key);
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static JsonObject readExistingRoot(Path path) {
        if (Files.notExists(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            return new JsonObject();
        }
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0xFFFFFF;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystal_tweaks.json");
    }
}
