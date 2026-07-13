package dev.zymekoh.kohscrystaltweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class KoHsCrystalTweaksConfig {
    private static final String FILE_NAME = "kohs_crystal_tweaks.json";
    private static final String SOUNDS_DIR = "kohs_crystal_tweaks";
    private static final String DEFAULT_FRAME_TINT_HEX = "#D9F4FF";
    private static final String DEFAULT_CORE_TINT_HEX = "#FF4FD8";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public boolean clientSideCrystalsEnabled = true;
    public boolean seamlessEnabled = true;
    public int predictionTimeoutTicks = 12;

    public boolean crystalTintEnabled = false;
    public String crystalFrameTintHex = DEFAULT_FRAME_TINT_HEX;
    public String crystalCoreTintHex = DEFAULT_CORE_TINT_HEX;
    public float crystalSpinSpeed = 1.0F;
    public boolean crystalFlotationEnabled = true;
    public boolean staticCrystalEnabled = false;
    public boolean placementFixEnabled = true;

    public boolean customSoundEnabled = true;
    public String customSoundFileName = "";
    public float soundVolume = 1.0F;
    public float soundSpeed = 1.0F;

    private static KoHsCrystalTweaksConfig instance;

    private KoHsCrystalTweaksConfig() {
    }

    public static synchronized KoHsCrystalTweaksConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static synchronized void save() {
        if (instance != null) {
            validate(instance);
            persist(instance);
        }
    }

    public static Path getSoundsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SOUNDS_DIR);
    }

    public static Path getCustomSoundPath() {
        String fileName = get().customSoundFileName;
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        return getSoundsDir().resolve(fileName).normalize();
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
        Path path = getPath();
        KoHsCrystalTweaksConfig config = new KoHsCrystalTweaksConfig();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                KoHsCrystalTweaksConfig loaded = GSON.fromJson(reader, KoHsCrystalTweaksConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception exception) {
                KoHsCrystalTweaks.LOGGER.error("[{}] Could not read config {}",
                        KoHsCrystalTweaks.MOD_ID, path, exception);
            }
        }

        validate(config);
        persist(config);
        return config;
    }

    private static void validate(KoHsCrystalTweaksConfig config) {
        config.predictionTimeoutTicks = Math.max(2, config.predictionTimeoutTicks);
        config.crystalFrameTintHex = normalizeHexColor(config.crystalFrameTintHex, DEFAULT_FRAME_TINT_HEX);
        config.crystalCoreTintHex = normalizeHexColor(config.crystalCoreTintHex, DEFAULT_CORE_TINT_HEX);
        config.crystalSpinSpeed = clamp(config.crystalSpinSpeed, 0.0F, 3.0F);
        if (config.customSoundFileName == null || config.customSoundFileName.isBlank()) {
            config.customSoundFileName = "";
        } else {
            Path fileName = Path.of(config.customSoundFileName).getFileName();
            config.customSoundFileName = fileName == null ? "" : fileName.toString();
        }
        config.soundVolume = clamp(config.soundVolume, 0.0F, 2.0F);
        config.soundSpeed = clamp(config.soundSpeed, 0.5F, 2.0F);
    }

    private static void persist(KoHsCrystalTweaksConfig config) {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception exception) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Could not write config {}",
                    KoHsCrystalTweaks.MOD_ID, path, exception);
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static int parseHexColor(String value, String fallback) {
        String normalized = normalizeHexColor(value, fallback);
        return 0xFF000000 | Integer.parseInt(normalized.substring(1), 16);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
