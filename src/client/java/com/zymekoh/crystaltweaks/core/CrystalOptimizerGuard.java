package com.zymekoh.crystaltweaks.core;

import java.util.List;
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
            // The retired KoHs Crystal Tweaks drives crystals from its own Connection mixin. Naming it
            // here turns a silent stand-down into a notice that says which JAR to remove.
            "kohs_crystal_tweaks",
            "kohscrystaltweaks",
    };

    /**
     * Visual-only crystal mods. They draw, they do not predict, so they are not a conflict.
     */
    private static final String[] VISUAL_ONLY_IDS = {
            "clientsidecrystals",
    };

    /**
     * Mods that optimize the game rather than the crystal.
     *
     * <p>Several of them live exactly where this mod lives: {@code Connection.send} for the network
     * ones, the entity and model path for the rendering ones. Overlapping there says nothing about
     * crystals, and treating it as a rival was what silently killed the optimizer for players whose
     * only crime was running a performance pack. They are named here so no amount of overlap, and no
     * future loosening of the heuristics below, can ever stand this mod down for one of them.</p>
     */
    private static final String[] PERFORMANCE_IDS = {
            "krypton",
            "lithium",
            "sodium",
            "c2me",
            "immediatelyfast",
            "scalablelux",
            "ferritecore",
            "moreculling",
            "entityculling",
            "modernfix",
            "lazydfu",
            "dynamicfps",
            "memoryleakfix",
            "noxesium",
            "viafabricplus",
            "viafabric",
            "viaversion",
    };

    private enum Status { CHECKING, READY, INCOMPLETE, CONFLICT }

    // Interaction helpers stay off only while the answer is still unknown, which lasts as long as the
    // JAR scan and no longer.
    private static volatile Status status = Status.CHECKING;
    private static volatile String detectedName = "";

    private CrystalOptimizerGuard() {
    }

    /**
     * True while the local interaction optimizations are allowed to run.
     *
     * <p>Only a mod actually found to be optimizing crystals turns these off. An unreadable JAR
     * somewhere in the pack is not that finding: it says the scan could not answer, and standing
     * down on no evidence disables the mod for players who have no conflict at all. So a scan that
     * cannot complete leaves the optimizations running and reports itself in the settings screen.</p>
     */
    public static boolean optimizationsAllowed() {
        return status == Status.READY || status == Status.INCOMPLETE;
    }

    public static boolean conflictDetected() {
        return status == Status.CONFLICT;
    }

    public static boolean scanPending() {
        return status == Status.CHECKING;
    }

    /** True when the scan finished without covering every mod, so the answer is a best effort. */
    public static boolean scanIncomplete() {
        return status == Status.INCOMPLETE;
    }

    public static synchronized void completeScan() {
        if (status == Status.CHECKING) status = Status.READY;
    }

    public static synchronized void reportScanIncomplete() {
        if (status == Status.CHECKING) status = Status.INCOMPLETE;
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

    /**
     * Decides whether a Mixin overlap is another crystal optimizer or merely a neighbour on the wire.
     *
     * <p>{@code Connection.send} is one of the busiest Mixin targets in the ecosystem: performance
     * mods, protocol translators and ping readouts all sit there without ever touching a crystal.
     * Landing on the same method is therefore not enough on its own. The mod also has to be about
     * crystals, by its id, its name, or the Mixin class doing the overlapping.</p>
     *
     * @param networkMixinClasses the foreign Mixin classes that overlap this mod on the interaction
     *                            path; empty when the overlap is somewhere else entirely
     */
    public static boolean overlapOptimizesCrystals(
            String modId,
            String modName,
            List<String> networkMixinClasses
    ) {
        if (networkMixinClasses == null || networkMixinClasses.isEmpty()) {
            return false;
        }
        if (optimizesTheGame(modId)) {
            return false;
        }
        if (mentionsCrystals(modId) || mentionsCrystals(modName)) {
            return true;
        }
        for (String mixinClass : networkMixinClasses) {
            if (mentionsCrystals(mixinClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsCrystals(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("crystal");
    }

    /** True for a mod that speeds the game up rather than driving crystals. Exact ids only. */
    public static boolean optimizesTheGame(String modId) {
        if (modId == null) {
            return false;
        }
        String id = modId.toLowerCase(Locale.ROOT);
        for (String performance : PERFORMANCE_IDS) {
            if (id.equals(performance)) {
                return true;
            }
        }
        return false;
    }

    /** Matches a mod id or name that names itself a crystal optimizer. */
    public static boolean looksLikeOptimizer(String modId, String modName) {
        String id = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
        if (optimizesTheGame(id)) {
            return false;
        }
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
