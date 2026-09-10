package com.zymekoh.crystaltweaks.core;

import java.util.Locale;

/**
 * Stands the local optimizations down when another mod is already optimizing crystals.
 *
 * <p>Two mods predicting the same interaction do not add up, they fight: each one acts on a world
 * the other has already changed, and the result is worse than either alone. Rather than rely on a
 * Mixin priority, which does not stop the other injection from running, this yields outright.</p>
 *
 * <p>What stands down is everything that touches interaction: the break prediction, the placement
 * stand-ins and the obsidian guard. Colors, sounds and the preview are untouched, because nothing
 * else can be fighting over those.</p>
 */
public final class CrystalOptimizerGuard {
    /**
     * Mod ids known to optimize crystal interaction. The behavioural check below catches the rest;
     * this list only shortens the path for the ones already known by name.
     */
    private static final String[] KNOWN_OPTIMIZER_IDS = {
            "marlowcrystal",
            "marlows_crystal_optimizer",
            "crystaloptimizer",
            "crystal_optimizer",
            "fastcrystal",
            "crystaloptimize",
            "nocrystalbreak",
    };

    /**
     * Visual-only crystal mods. They draw, they do not predict, so they are not a conflict.
     */
    private static final String[] VISUAL_ONLY_IDS = {
            "clientsidecrystals",
    };

    private enum Status { CHECKING, READY, CONFLICT, FAILED }

    // Interaction helpers stay off until the complete compatibility scan succeeds.
    private static volatile Status status = Status.CHECKING;
    private static volatile String detectedName = "";

    private CrystalOptimizerGuard() {
    }

    /** True while the local interaction optimizations are allowed to run. */
    public static boolean optimizationsAllowed() {
        return status == Status.READY;
    }

    public static boolean conflictDetected() {
        return status == Status.CONFLICT;
    }

    public static boolean scanPending() {
        return status == Status.CHECKING;
    }

    public static boolean scanFailed() {
        return status == Status.FAILED;
    }

    public static synchronized void completeScan() {
        if (status == Status.CHECKING) status = Status.READY;
    }

    public static synchronized void reportScanFailure() {
        if (status != Status.CONFLICT) status = Status.FAILED;
    }

    /** Display name of the mod that caused the stand-down, for the warning in the settings screen. */
    public static String conflictingModName() {
        return detectedName;
    }

    public static synchronized void reportConflict(String modName) {
        detectedName = modName == null ? "" : modName;
        status = Status.CONFLICT;
    }

    public static synchronized void clearConflict() {
        status = Status.CHECKING;
        detectedName = "";
    }

    /** Matches a mod id or name that names itself a crystal optimizer. */
    public static boolean looksLikeOptimizer(String modId, String modName) {
        String id = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
        for (String visual : VISUAL_ONLY_IDS) {
            if (id.equals(visual)) {
                return false;
            }
        }
        for (String known : KNOWN_OPTIMIZER_IDS) {
            if (id.equals(known)) {
                return true;
            }
        }

        String haystack = (id + " " + (modName == null ? "" : modName)).toLowerCase(Locale.ROOT);
        if (!haystack.contains("crystal")) {
            return false;
        }
        return haystack.contains("optimi") || haystack.contains("aura") || haystack.contains("fast");
    }
}
