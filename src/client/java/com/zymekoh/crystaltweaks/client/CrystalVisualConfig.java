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
    private static volatile int outerColor = DEFAULT_COLOR;
    private static volatile int innerColor = DEFAULT_COLOR;
    private static volatile int coreColor = DEFAULT_COLOR;
    private static volatile int rotationSpeedPercent = 100;
    private static volatile int floatingSpeedPercent = 100;
    private static volatile boolean customSoundEnabled;
    private static volatile String customSoundFileName = "";
    private static volatile float soundVolume = 1.0F;
    private static volatile float soundSpeed = 1.0F;
    private static volatile boolean loaded;

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
                    outerColor = parseColor(visuals, "outerColor", DEFAULT_COLOR);
                    innerColor = parseColor(visuals, "innerColor", DEFAULT_COLOR);
                    coreColor = parseColor(visuals, "coreColor", DEFAULT_COLOR);
                    rotationSpeedPercent = clamp(intValue(visuals, "rotationSpeedPercent", 100), 0, 300);
                    floatingSpeedPercent = clamp(intValue(visuals, "floatingSpeedPercent", 100), 0, 300);

                    JsonObject sounds = root.has("sounds") && root.get("sounds").isJsonObject()
                            ? root.getAsJsonObject("sounds")
                            : new JsonObject();
                    customSoundEnabled = booleanValue(sounds, "enabled", false);
                    customSoundFileName = stringValue(sounds, "file", "");
                    soundVolume = clamp(floatValue(sounds, "volume", 1.0F), 0.0F, 2.0F);
                    soundSpeed = clamp(floatValue(sounds, "speed", 1.0F), 0.5F, 2.0F);
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
                visuals.addProperty("outerColor", toHex(outerColor));
                visuals.addProperty("innerColor", toHex(innerColor));
                visuals.addProperty("coreColor", toHex(coreColor));
                visuals.addProperty("rotationSpeedPercent", rotationSpeedPercent);
                visuals.addProperty("floatingSpeedPercent", floatingSpeedPercent);
                root.add("visuals", visuals);

                root.remove("tweaks");

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
        return outerColor;
    }

    public static int innerColor() {
        load();
        return innerColor;
    }

    public static int coreColor() {
        load();
        return coreColor;
    }

    public static void setOuterColor(int color) {
        load();
        outerColor = opaque(color);
    }

    public static void setInnerColor(int color) {
        load();
        innerColor = opaque(color);
    }

    public static void setCoreColor(int color) {
        load();
        coreColor = opaque(color);
    }

    public static void resetColors() {
        outerColor = DEFAULT_COLOR;
        innerColor = DEFAULT_COLOR;
        coreColor = DEFAULT_COLOR;
    }

    public static int rotationSpeedPercent() {
        load();
        return rotationSpeedPercent;
    }

    public static void setRotationSpeedPercent(int percent) {
        load();
        rotationSpeedPercent = clamp(percent, 0, 300);
    }

    public static int floatingSpeedPercent() {
        load();
        return floatingSpeedPercent;
    }

    public static void setFloatingSpeedPercent(int percent) {
        load();
        floatingSpeedPercent = clamp(percent, 0, 300);
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
