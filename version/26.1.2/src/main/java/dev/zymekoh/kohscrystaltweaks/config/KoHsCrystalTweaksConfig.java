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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_FRAME_TINT_HEX = "#D9F4FF";
    private static final String DEFAULT_CORE_TINT_HEX = "#FF4FD8";

    // ── Optimization ──
    public boolean clientSideCrystalsEnabled = false;
    public boolean seamlessEnabled = false;
    public int predictionTimeoutTicks = 12;

    // ── Crystal Visuals ──
    public boolean crystalTintEnabled = false;
    public String crystalFrameTintHex = DEFAULT_FRAME_TINT_HEX;
    public String crystalCoreTintHex = DEFAULT_CORE_TINT_HEX;
    public float crystalSpinSpeed = 1.0f;
    public boolean crystalFlotationEnabled = true;
    public boolean staticCrystalEnabled = false;
    public boolean placementFixEnabled = true;
    public boolean safeCrystalEnabled = false;
    public boolean forceCrystalPvpPriorityEnabled = false;

    // ── Sound Crystal ──
    public boolean customSoundEnabled = true;
    public String customSoundFileName = "";
    public float soundVolume = 1.0f;
    public float soundSpeed = 1.0f;

    private static KoHsCrystalTweaksConfig instance;

    // ══════════════════════════════════════════════════════════════════
    //  Access
    // ══════════════════════════════════════════════════════════════════

    public static KoHsCrystalTweaksConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void save() {
        if (instance == null) return;
        persist(instance);
    }

    // ── Optimization setters ──

    public static void setClientSideCrystalsEnabled(boolean enabled) {
        KoHsCrystalTweaksConfig c = get();
        c.clientSideCrystalsEnabled = enabled;
        persist(c);
    }

    // ── Crystal Visuals setters ──

    public static void setCrystalTintSettings(boolean enabled, String frameHex, String coreHex) {
        KoHsCrystalTweaksConfig c = get();
        c.crystalTintEnabled = enabled;
        c.crystalFrameTintHex = normalizeHexColor(frameHex, DEFAULT_FRAME_TINT_HEX);
        c.crystalCoreTintHex = normalizeHexColor(coreHex, DEFAULT_CORE_TINT_HEX);
        persist(c);
    }

    public static void setCrystalTweaksSettings(float spinSpeed, boolean flotationEnabled, boolean staticEnabled) {
        KoHsCrystalTweaksConfig c = get();
        c.crystalSpinSpeed = Math.max(0f, Math.min(3f, spinSpeed));
        c.crystalFlotationEnabled = flotationEnabled;
        c.staticCrystalEnabled = staticEnabled;
        persist(c);
    }

    // ── Sound Crystal setters ──

    public static void setCustomSoundSettings(boolean enabled, String path, float volume, float speed) {
        KoHsCrystalTweaksConfig cfg = get();
        cfg.customSoundEnabled = enabled;
        cfg.customSoundFileName = path != null ? path : "";
        cfg.soundVolume = Math.max(0f, Math.min(2f, volume));
        cfg.soundSpeed = Math.max(0.5f, Math.min(2f, speed));
        persist(cfg);
    }

    // ── Paths ──

    public static Path getSoundsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(SOUNDS_DIR);
    }

    public static Path getCustomSoundPath() {
        String name = get().customSoundFileName;
        if (name == null || name.isEmpty()) return null;
        return getSoundsDir().resolve(name);
    }

    // ── Color helpers ──

    public static int getCrystalFrameTintArgb() {
        return parseHexColor(get().crystalFrameTintHex, DEFAULT_FRAME_TINT_HEX);
    }

    public static int getCrystalCoreTintArgb() {
        return parseHexColor(get().crystalCoreTintHex, DEFAULT_CORE_TINT_HEX);
    }

    public static String normalizeHexColor(String value, String fallback) {
        String safeFallback = fallback == null ? "#FFFFFF" : fallback.trim().toUpperCase(Locale.ROOT);
        if (!safeFallback.matches("#[0-9A-F]{6}")) safeFallback = "#FFFFFF";
        if (value == null) return safeFallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if (!normalized.matches("[0-9A-F]{6}")) return safeFallback;
        return "#" + normalized;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Load / Save
    // ══════════════════════════════════════════════════════════════════

    private static KoHsCrystalTweaksConfig load() {
        KoHsCrystalTweaksConfig config = new KoHsCrystalTweaksConfig();
        Path path = getPath();

        if (!Files.exists(path)) {
            persist(config);
            return config;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            KoHsCrystalTweaksConfig loaded = GSON.fromJson(reader, KoHsCrystalTweaksConfig.class);
            if (loaded != null) {
                config.clientSideCrystalsEnabled = loaded.clientSideCrystalsEnabled;
                config.seamlessEnabled = loaded.seamlessEnabled;
                config.predictionTimeoutTicks = Math.max(2, loaded.predictionTimeoutTicks);
                config.crystalTintEnabled = loaded.crystalTintEnabled;
                config.crystalFrameTintHex = normalizeHexColor(loaded.crystalFrameTintHex, DEFAULT_FRAME_TINT_HEX);
                config.crystalCoreTintHex = normalizeHexColor(loaded.crystalCoreTintHex, DEFAULT_CORE_TINT_HEX);
                config.crystalSpinSpeed = Math.max(0f, Math.min(3f, loaded.crystalSpinSpeed));
                config.crystalFlotationEnabled = loaded.crystalFlotationEnabled;
                config.staticCrystalEnabled = loaded.staticCrystalEnabled;
                config.placementFixEnabled = loaded.placementFixEnabled;
                config.safeCrystalEnabled = loaded.safeCrystalEnabled;
                config.forceCrystalPvpPriorityEnabled = loaded.forceCrystalPvpPriorityEnabled;
                config.customSoundEnabled = loaded.customSoundEnabled;
                config.customSoundFileName = loaded.customSoundFileName != null ? loaded.customSoundFileName : "";
                config.soundVolume = Math.max(0f, Math.min(2f, loaded.soundVolume));
                config.soundSpeed = Math.max(0.5f, Math.min(2f, loaded.soundSpeed));
            }
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Config read error {}: {}",
                    KoHsCrystalTweaks.MOD_ID, path, e.toString());
        }

        persist(config);
        return config;
    }

    private static void persist(KoHsCrystalTweaksConfig config) {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Config write error {}: {}",
                    KoHsCrystalTweaks.MOD_ID, path, e.toString());
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
