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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class CrystalVisualConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final Object LOCK = new Object();
    private static volatile boolean customSoundEnabled;
    private static volatile String customSoundFileName = "";
    private static volatile float soundVolume = 1.0F;
    private static volatile float soundSpeed = 1.0F;
    private static volatile boolean ghostCrystals;
    private static volatile boolean loaded;
    private static final CrystalAppearance playerVisuals = new CrystalAppearance();
    private static CrystalAppearance enemyVisuals = new CrystalAppearance();
    private static boolean enemyCustomEnabled;

    public static CrystalAppearance visuals(boolean enemy) {
        load();
        return enemy ? enemyVisuals : playerVisuals;
    }

    public static boolean enemyCustomEnabled() { load(); return enemyCustomEnabled; }
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
                    playerVisuals.outerColor = parseColor(visuals, "outerColor", DEFAULT_COLOR);
                    playerVisuals.innerColor = parseColor(visuals, "innerColor", DEFAULT_COLOR);
                    playerVisuals.coreColor = parseColor(visuals, "coreColor", DEFAULT_COLOR);
                    playerVisuals.rotationSpeedPercent = clamp(intValue(visuals, "rotationSpeedPercent", 100), 0, 300);
                    playerVisuals.floatingSpeedPercent = clamp(intValue(visuals, "floatingSpeedPercent", 100), 0, 300);

                    JsonObject sounds = root.has("sounds") && root.get("sounds").isJsonObject()
                            ? root.getAsJsonObject("sounds")
                            : new JsonObject();
                    customSoundEnabled = booleanValue(sounds, "enabled", false);
                    customSoundFileName = stringValue(sounds, "file", "");
                    soundVolume = clamp(floatValue(sounds, "volume", 1.0F), 0.0F, 2.0F);
                    soundSpeed = clamp(floatValue(sounds, "speed", 1.0F), 0.5F, 2.0F);

                    JsonObject gameplay = root.has("gameplay") && root.get("gameplay").isJsonObject()
                            ? root.getAsJsonObject("gameplay")
                            : new JsonObject();
                    ghostCrystals = booleanValue(gameplay, "ghostCrystals", false);

                    JsonObject glow = root.has("glow") && root.get("glow").isJsonObject()
                            ? root.getAsJsonObject("glow")
                            : new JsonObject();
                    playerVisuals.glowReflectionsPercent = clamp(intValue(glow, "reflectionsPercent", 0), 0, 300);
                    JsonObject enemy = root.has("enemyVisuals") && root.get("enemyVisuals").isJsonObject()
                            ? root.getAsJsonObject("enemyVisuals") : new JsonObject();
                    enemyCustomEnabled = booleanValue(enemy, "enabled", false);
                    try {
                        enemyVisuals = GSON.fromJson(enemy, CrystalAppearance.class).copy();
                    } catch (RuntimeException invalidEnemySettings) {
                        enemyVisuals = new CrystalAppearance();
                        CrystalTweaksClient.LOGGER.warn("Invalid enemy visual settings; resetting only that profile");
                    }
                    playerVisuals.glowPowerPercent = clamp(intValue(glow, "powerPercent", 0), 0, 300);
                    playerVisuals.customGlowColor = booleanValue(glow, "customColor", false);
                    playerVisuals.glowColor = parseColor(glow, "color", DEFAULT_COLOR);
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
                root.add("visuals", visuals);

                root.remove("tweaks");

                JsonObject gameplay = root.has("gameplay") && root.get("gameplay").isJsonObject()
                        ? root.getAsJsonObject("gameplay")
                        : new JsonObject();
                // 2.2.7 briefly stored an instant-break toggle here; that behaviour is core now.
                gameplay.remove("predictCrystalBreak");
                gameplay.addProperty("ghostCrystals", ghostCrystals);
                root.add("gameplay", gameplay);

                JsonObject glow = root.has("glow") && root.get("glow").isJsonObject()
                        ? root.getAsJsonObject("glow")
                        : new JsonObject();
                glow.addProperty("reflectionsPercent", playerVisuals.glowReflectionsPercent);
                JsonObject enemy = GSON.toJsonTree(enemyVisuals.copy()).getAsJsonObject();
                enemy.addProperty("enabled", enemyCustomEnabled);
                root.add("enemyVisuals", enemy);
                glow.addProperty("powerPercent", playerVisuals.glowPowerPercent);
                glow.addProperty("customColor", playerVisuals.customGlowColor);
                glow.addProperty("color", toHex(playerVisuals.glowColor));
                root.add("glow", glow);

                JsonObject sounds = root.has("sounds") && root.get("sounds").isJsonObject()
                        ? root.getAsJsonObject("sounds")
                        : new JsonObject();
                sounds.addProperty("enabled", customSoundEnabled);
                sounds.addProperty("file", customSoundFileName);
                sounds.addProperty("volume", soundVolume);
                sounds.addProperty("speed", soundSpeed);
                root.add("sounds", sounds);

                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
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
        playerVisuals.outerColor = DEFAULT_COLOR;
        playerVisuals.innerColor = DEFAULT_COLOR;
        playerVisuals.coreColor = DEFAULT_COLOR;
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

    public static void setSoundVolume(float volume) {
        load();
        soundVolume = clamp(volume, 0.0F, 2.0F);
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
     * Draws a stand-in crystal while the server's real one is still in flight. Purely visual: it is
     * never an entity, so nothing Vanilla checks and no packet can see it.
     *
     * <p>Off until the player turns it on. It changes what the world looks like rather than only how
     * quickly it catches up, and some servers ask that nothing be drawn that the server has not sent
     * yet, so opting in is the player's call to make.</p>
     */
    public static boolean ghostCrystals() {
        load();
        return ghostCrystals;
    }

    public static void setGhostCrystals(boolean enabled) {
        load();
        ghostCrystals = enabled;
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
