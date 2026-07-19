package dev.zymekoh.kohscrystaltweaks.compat;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import java.util.List;

/** Holds the startup decision for conflicts limited to Force Crystal PvP Priority. */
public final class ForceCrystalPriorityCompatibility {
    private static volatile boolean initialized;
    private static volatile boolean startupDecisionPending;
    private static List<ForceCrystalPriorityConflictScanner.Conflict> startupConflicts = List.of();

    private ForceCrystalPriorityCompatibility() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!KoHsCrystalTweaksConfig.get().forceCrystalPvpPriorityEnabled) {
            return;
        }

        startupConflicts = ForceCrystalPriorityConflictScanner.scanInstalledMods();
        startupDecisionPending = !startupConflicts.isEmpty();
        for (ForceCrystalPriorityConflictScanner.Conflict conflict : startupConflicts) {
            KoHsCrystalTweaks.LOGGER.warn(
                    "Force Crystal PvP Priority requires confirmation because {} ({}) {} overlaps its input hook",
                    conflict.modName(), conflict.modId(), conflict.version());
            for (ForceCrystalPriorityConflictScanner.ConflictPoint point : conflict.points()) {
                KoHsCrystalTweaks.LOGGER.warn("Priority overlap: {}#{} via {}",
                        point.targetClass(), point.targetMethod(), point.mixinClass());
            }
        }
    }

    public static boolean isStartupDecisionPending() {
        initialize();
        return startupDecisionPending;
    }

    public static boolean isRuntimeAllowed() {
        initialize();
        return !startupDecisionPending;
    }

    public static List<ForceCrystalPriorityConflictScanner.Conflict> getStartupConflicts() {
        initialize();
        return startupConflicts;
    }

    public static void continueAnyway() {
        startupDecisionPending = false;
    }

    public static void cancelAndDisable() {
        KoHsCrystalTweaksConfig.get().forceCrystalPvpPriorityEnabled = false;
        KoHsCrystalTweaksConfig.save();
        startupDecisionPending = false;
    }
}
