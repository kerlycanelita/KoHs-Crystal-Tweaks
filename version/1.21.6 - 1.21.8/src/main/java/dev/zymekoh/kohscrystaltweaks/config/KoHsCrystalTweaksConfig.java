package dev.zymekoh.kohscrystaltweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class KoHsCrystalTweaksConfig {
    public static final int CURRENT_CONFIG_VERSION = 2;

    private static final String FILE_NAME = "kohs_crystal_tweaks.json";
    private static final String SOUNDS_DIR = "kohs_crystal_tweaks";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_FRAME_TINT_HEX = "#D9F4FF";
    private static final String DEFAULT_CORE_TINT_HEX = "#FF4FD8";

    public int configVersion = CURRENT_CONFIG_VERSION;

    // Optional local presentation. Both are disabled for a fresh installation.
    public boolean clientSideCrystalsEnabled = false;
    public boolean seamlessEnabled = false;
    public int predictionTimeoutTicks = 12;

    // Crystal visuals.
    public boolean crystalTintEnabled = false;
    public String crystalFrameTintHex = DEFAULT_FRAME_TINT_HEX;
    public String crystalCoreTintHex = DEFAULT_CORE_TINT_HEX;
    public float crystalSpinSpeed = 1.0f;
    public boolean crystalFlotationEnabled = true;
    public boolean staticCrystalEnabled = false;

    // Gameplay safeguards.
    public boolean safeCrystalEnabled = false;
    public boolean confirmedCrystalCleanupEnabled = false;

    // Crystal sound.
    public boolean customSoundEnabled = true;
    public String customSoundFileName = "";
    public float soundVolume = 1.0f;
    public float soundSpeed = 1.0f;

    private static KoHsCrystalTweaksConfig instance;

    private KoHsCrystalTweaksConfig() {
    }

    public static KoHsCrystalTweaksConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void save() {
        if (instance != null) {
            persist(instance);
        }
    }

    public static boolean isConfirmedCrystalCleanupEnabled() {
        return get().confirmedCrystalCleanupEnabled;
    }

    public static void setClientSideCrystalsEnabled(boolean enabled) {
        KoHsCrystalTweaksConfig config = get();
        config.clientSideCrystalsEnabled = enabled;
        if (!enabled) {
            config.seamlessEnabled = false;
        }
        persist(config);
    }

    public static void setCrystalTintSettings(boolean enabled, String frameHex, String coreHex) {
        KoHsCrystalTweaksConfig config = get();
        config.crystalTintEnabled = enabled;
        config.crystalFrameTintHex = normalizeHexColor(frameHex, DEFAULT_FRAME_TINT_HEX);
        config.crystalCoreTintHex = normalizeHexColor(coreHex, DEFAULT_CORE_TINT_HEX);
        persist(config);
    }

    public static void setCrystalTweaksSettings(float spinSpeed, boolean flotationEnabled, boolean staticEnabled) {
        KoHsCrystalTweaksConfig config = get();
        config.crystalSpinSpeed = Math.max(0.0f, Math.min(3.0f, spinSpeed));
        config.crystalFlotationEnabled = flotationEnabled;
        config.staticCrystalEnabled = staticEnabled;
        persist(config);
    }

    public static void setCustomSoundSettings(boolean enabled, String path, float volume, float speed) {
        KoHsCrystalTweaksConfig config = get();
        config.customSoundEnabled = enabled;
        config.customSoundFileName = path != null ? path : "";
        config.soundVolume = Math.max(0.0f, Math.min(2.0f, volume));
        config.soundSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        persist(config);
    }

    public static Path getSoundsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SOUNDS_DIR);
    }

    public static Path getCustomSoundPath() {
        String name = get().customSoundFileName;
        if (name == null || name.isEmpty()) {
            return null;
        }
        return getSoundsDir().resolve(name);
    }

    public static int getCrystalFrameTintArgb() {
        return parseHexColor(get().crystalFrameTintHex, DEFAULT_FRAME_TINT_HEX);
    }

    public static int getCrystalCoreTintArgb() {
        return parseHexColor(get().crystalCoreTintHex, DEFAULT_CORE_TINT_HEX);
    }

    public static String normalizeHexColor(String value, String fallback) {
        String safeFallback = fallback == null ? "#FFFFFF" : fallback.trim().toUpperCase(Locale.ROOT);
        if (!safeFallback.matches("#[0-9A-F]{6}")) {
            safeFallback = "#FFFFFF";
        }
        if (value == null) {
            return safeFallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches("[0-9A-F]{6}") ? "#" + normalized : safeFallback;
    }

    private static KoHsCrystalTweaksConfig load() {
        KoHsCrystalTweaksConfig config = new KoHsCrystalTweaksConfig();
        Path path = getPath();
        if (!Files.exists(path)) {
            persist(config);
            return config;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            boolean legacy = !json.has("configVersion")
                    || json.get("configVersion").getAsInt() < CURRENT_CONFIG_VERSION;
            KoHsCrystalTweaksConfig loaded = GSON.fromJson(json, KoHsCrystalTweaksConfig.class);
            if (loaded != null) {
                copySafeSettings(config, loaded);
                if (!legacy) {
                    config.clientSideCrystalsEnabled = loaded.clientSideCrystalsEnabled;
                    config.seamlessEnabled =
                            loaded.clientSideCrystalsEnabled && loaded.seamlessEnabled;
                    config.confirmedCrystalCleanupEnabled = loaded.confirmedCrystalCleanupEnabled;
                }
            }
        } catch (Exception exception) {
            KoHsCrystalTweaks.LOGGER.error(
                    "[{}] Config read error {}: {}",
                    KoHsCrystalTweaks.MOD_ID,
                    path,
                    exception.toString());
        }

        // Legacy acceleration, replay, retarget and slot-priority keys are intentionally discarded.
        config.configVersion = CURRENT_CONFIG_VERSION;
        persist(config);
        return config;
    }

    private static void copySafeSettings(
            KoHsCrystalTweaksConfig target,
            KoHsCrystalTweaksConfig source) {
        target.predictionTimeoutTicks = Math.max(2, source.predictionTimeoutTicks);
        target.crystalTintEnabled = source.crystalTintEnabled;
        target.crystalFrameTintHex =
                normalizeHexColor(source.crystalFrameTintHex, DEFAULT_FRAME_TINT_HEX);
        target.crystalCoreTintHex =
                normalizeHexColor(source.crystalCoreTintHex, DEFAULT_CORE_TINT_HEX);
        target.crystalSpinSpeed = Math.max(0.0f, Math.min(3.0f, source.crystalSpinSpeed));
        target.crystalFlotationEnabled = source.crystalFlotationEnabled;
        target.staticCrystalEnabled = source.staticCrystalEnabled;
        target.safeCrystalEnabled = source.safeCrystalEnabled;
        target.customSoundEnabled = source.customSoundEnabled;
        target.customSoundFileName =
                source.customSoundFileName != null ? source.customSoundFileName : "";
        target.soundVolume = Math.max(0.0f, Math.min(2.0f, source.soundVolume));
        target.soundSpeed = Math.max(0.5f, Math.min(2.0f, source.soundSpeed));
    }

    private static void persist(KoHsCrystalTweaksConfig config) {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception exception) {
            KoHsCrystalTweaks.LOGGER.error(
                    "[{}] Config write error {}: {}",
                    KoHsCrystalTweaks.MOD_ID,
                    path,
                    exception.toString());
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static int parseHexColor(String value, String fallback) {
        String normalized = normalizeHexColor(value, fallback);
        return 0xFF000000 | Integer.parseInt(normalized.substring(1), 16);
    }
}
