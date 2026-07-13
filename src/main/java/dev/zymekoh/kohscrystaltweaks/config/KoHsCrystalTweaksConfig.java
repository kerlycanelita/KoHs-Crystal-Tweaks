package dev.zymekoh.kohscrystaltweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class KoHsCrystalTweaksConfig {
    private static final String FILE_NAME = "kohs_crystal_tweaks.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public boolean clientSideCrystalsEnabled = false;
    public boolean seamlessEnabled = true;
    public int predictionTimeoutTicks = 12;

    private static KoHsCrystalTweaksConfig instance;

    public static KoHsCrystalTweaksConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void save() {
        if (instance == null) {
            return;
        }
        save(instance);
    }

    public static void setClientSideCrystalsEnabled(boolean enabled) {
        KoHsCrystalTweaksConfig config = get();
        config.clientSideCrystalsEnabled = enabled;
        save(config);
    }

    private static KoHsCrystalTweaksConfig load() {
        KoHsCrystalTweaksConfig config = new KoHsCrystalTweaksConfig();
        Path path = getPath();

        if (!Files.exists(path)) {
            save(config);
            return config;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            KoHsCrystalTweaksConfig loaded = GSON.fromJson(reader, KoHsCrystalTweaksConfig.class);
            if (loaded != null) {
                config.clientSideCrystalsEnabled = loaded.clientSideCrystalsEnabled;
                config.seamlessEnabled = loaded.seamlessEnabled;
                config.predictionTimeoutTicks = Math.max(2, loaded.predictionTimeoutTicks);
            }
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Config read error {}: {}", KoHsCrystalTweaks.MOD_ID, path, e.toString());
        }

        save(config);
        return config;
    }

    private static void save(KoHsCrystalTweaksConfig config) {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Config write error {}: {}", KoHsCrystalTweaks.MOD_ID, path, e.toString());
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
