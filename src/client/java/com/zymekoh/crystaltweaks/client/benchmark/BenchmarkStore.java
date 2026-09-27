package com.zymekoh.crystaltweaks.client.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The finished runs, newest last, in {@code config/crystal_tweaks/benchmarks.json}. Kept across
 * restarts so a run with one optimizer can be compared with a run taken after swapping it out.
 */
public final class BenchmarkStore {
    private static final int MAX_RUNS = 12;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<BenchmarkRun> runs;

    private BenchmarkStore() {
    }

    public static synchronized List<BenchmarkRun> runs() {
        load();
        return List.copyOf(runs);
    }

    public static synchronized void add(BenchmarkRun run) {
        load();
        runs.add(run);
        while (runs.size() > MAX_RUNS) {
            runs.remove(0);
        }
        save();
    }

    public static synchronized void clear() {
        load();
        runs.clear();
        save();
    }

    /** The newest run whose optimizer differs from {@code run}'s, for the side-by-side comparison. */
    public static synchronized BenchmarkRun comparisonFor(BenchmarkRun run) {
        load();
        if (run == null) {
            return null;
        }
        for (int index = runs.size() - 1; index >= 0; index--) {
            BenchmarkRun other = runs.get(index);
            if (other.timestamp() != run.timestamp() && !other.optimizer().equals(run.optimizer())) {
                return other;
            }
        }
        return null;
    }

    private static void load() {
        if (runs != null) {
            return;
        }
        runs = new ArrayList<>();
        Path path = path();
        if (Files.notExists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<BenchmarkRun> stored = GSON.fromJson(reader, new TypeToken<List<BenchmarkRun>>() { }.getType());
            if (stored != null) {
                for (BenchmarkRun run : stored) {
                    if (run != null && run.optimizer() != null) {
                        runs.add(run);
                    }
                }
            }
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Could not read saved benchmark runs from {}", path, exception);
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(runs, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Could not save benchmark runs to {}", path, exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystal_tweaks").resolve("benchmarks.json");
    }
}
