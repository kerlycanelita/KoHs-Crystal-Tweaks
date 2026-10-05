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
     * Mod ids known to drive crystal interaction on the client. The name and Mixin checks below
     * catch the rest; this list covers the popular ones whatever they call themselves.
     */
    private static final String[] KNOWN_OPTIMIZER_IDS = {
            "marlowcrystal",
            "marlows_crystal_optimizer",
            "crystaloptimizer",
            "crystal_optimizer",
            "fastcrystal",
            "crystaloptimize",
            "nocrystalbreak",
            // HCsCR removes a hit crystal before the server answers, as the break prediction does.
            "hcscr",
            // Client Side Crystals draws a stand-in crystal the moment one is placed, which is what
            // the ghost crystals do, and its stand-in would be matched as the player's placement,
            // leaving the real crystal to read as someone else's.
            "clientsidecrystals",
            // Client-Sided Crystals lets the client interact with crystals ahead of the server.
            "clientsidedcrystals",
            // The retired KoHs Crystal Tweaks drives crystals from its own Connection mixin. Naming it
            // here turns a silent stand-down into a notice that says which JAR to remove.
            "kohs_crystal_tweaks",
            "kohscrystaltweaks",
    };

    /**
     * Optimizers whose name says what they do without the words below, matched on the id and the
     * name with spaces and punctuation ignored. Make My Crystals Faster hides an attacked crystal
     * the way the break prediction does; Zero Delay Crystals calls itself Crystals Optimized.
     */
    private static final String[] KNOWN_OPTIMIZER_NAMES = {
            "makemycrystalsfaster",
            "zerodelaycrystal",
            "crystalsoptimized",
    };

    /**
     * Words that, next to "crystal", describe driving crystals ahead of the server. "Fast" is not
     * one of them: FastCrystalSpin, Crystal Speed and their like only change how a crystal looks.
     */
    private static final String[] OPTIMIZER_WORDS = {
            "optimi",
            "client side",
            "clientside",
            "client-side",
            "client_side",
            "client sided",
            "client-sided",
            "clientsided",
            "zero delay",
            "zerodelay",
    };

    /**
     * What a crystal optimizer's own Mixin class tends to be named after. Only read for a Mixin that
     * already overlaps this mod's interaction path. Attacking, breaking and placing are not on the
     * list: a mod that keeps you from breaking your own crystals, or one that only counts them,
     * names its Mixins after those too, and neither optimizes anything.
     */
    private static final String[] INTERACTION_WORDS = {
            "optimi",
            "clientside",
            "client_side",
            "predict",
    };

    /**
     * Crystal mods that do things with crystals but optimize nothing: protections, skins, glows,
     * spins, sizes, sounds and counters. A match here outranks the Mixin rule, so none of them can
     * pause the optimizations however its Mixins are named. Matched on the id and the name, with
     * spaces, dashes, dots, apostrophes and underscores ignored.
     */
    private static final String[] NOT_OPTIMIZER_WORDS = {
            "safecrystal",
            "crystalsafe",
            "crystalprotect",
            "protectcrystal",
            "crystalglow",
            "glowcrystal",
            "crystalcustom",
            "customcrystal",
            "crystalskin",
            "crystaltexture",
            "crystalcolor",
            "crystalcolour",
            "crystalspin",
            "crystalspeed",
            "crystalsize",
            "smallercrystal",
            "biggercrystal",
            "crystalsound",
            "crystalparticle",
            "crystalcount",
            "crystalhud",
            "nocrystaldamage",
    };

    /**
     * Crystal mods that read the same packets to count or display them. A crystals-per-second
     * counter hooks {@code Connection.send} and says "crystal" everywhere, and it predicts nothing.
     */
    private static final String[] READOUT_WORDS = {
            "count",
            "cps",
            "persecond",
            "per_second",
            "hud",
            "stat",
            "display",
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
            "packetfixer",
            "badoptimizations",
            "vmp",
            "nvidium",
            "sodium-extra",
            "iris",
            "gpu_booster",
            "ixeris",
            "particle_core",
    };

    private enum Status { CHECKING, READY, INCOMPLETE, CONFLICT }

    /** Why the interaction helpers are not running, for the settings screen to say so. */
    public enum PauseReason { NONE, CHECKING, CONFLICT, FORCED_OFF }

    // Interaction helpers stay off only while the answer is still unknown, which lasts as long as the
    // JAR scan and no longer.
    private static volatile Status status = Status.CHECKING;
    private static volatile String detectedName = "";
    private static volatile String detectedId = "";
    /** The player's own "Force off" switch, which outranks whatever the scan finds. */
    private static volatile boolean forcedOff;

    private CrystalOptimizerGuard() {
    }

    /** Set from the configuration: every crystal optimization stays off while this is true. */
    public static void setForcedOff(boolean off) {
        forcedOff = off;
    }

    public static boolean forcedOff() {
        return forcedOff;
    }

    /**
     * The one reason the helpers are paused, if they are. The player's own switch comes first: it
     * holds whatever the scan says, so naming a detected mod would point at the wrong cause.
     */
    public static PauseReason pauseReason() {
        if (forcedOff) {
            return PauseReason.FORCED_OFF;
        }
        return switch (status) {
            case CONFLICT -> PauseReason.CONFLICT;
            case CHECKING -> PauseReason.CHECKING;
            default -> PauseReason.NONE;
        };
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
        return !forcedOff && (status == Status.READY || status == Status.INCOMPLETE);
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

    /** The detected mod's id, for its icon in the settings screen; empty when there is none. */
    public static String conflictingModId() {
        return detectedId;
    }

    public static synchronized void reportConflict(String modName) {
        reportConflict(modName, "");
    }

    public static synchronized void reportConflict(String modName, String modId) {
        detectedName = modName == null ? "" : modName;
        detectedId = modId == null ? "" : modId;
        status = Status.CONFLICT;
    }

    public static synchronized void clearConflict() {
        status = Status.CHECKING;
        detectedName = "";
        detectedId = "";
    }

    /**
     * Decides whether a Mixin overlap is another crystal optimizer or merely a neighbour on the wire.
     *
     * <p>{@code Connection.send} is one of the busiest Mixin targets in the ecosystem: performance
     * mods, protocol translators and ping readouts all sit there without ever touching a crystal.
     * Landing on the same method is therefore not enough on its own, and neither is saying
     * "crystal": crystals-per-second counters and End Crystal skins do that too. The mod has to name
     * itself a crystal optimizer, or the overlapping Mixin has to be about acting on crystals.</p>
     *
     * @param mixinClasses the foreign Mixin classes that overlap this mod on the interaction or
     *                     crystal rendering path; empty when the overlap is somewhere else entirely
     */
    public static boolean overlapOptimizesCrystals(
            String modId,
            String modName,
            List<String> mixinClasses
    ) {
        if (mixinClasses == null || mixinClasses.isEmpty()) {
            return false;
        }
        if (optimizesTheGame(modId)) {
            return false;
        }
        if (looksLikeOptimizer(modId, modName)) {
            return true;
        }
        if (doesThingsWithCrystals(modId, modName)) {
            return false;
        }
        for (String mixinClass : mixinClasses) {
            if (actsOnCrystals(mixinClass)) {
                return true;
            }
        }
        return false;
    }

    /** A Mixin class whose own name says it acts on crystals, and not that it counts them. */
    private static boolean actsOnCrystals(String mixinClass) {
        if (mixinClass == null) {
            return false;
        }
        String simpleName = mixinClass.substring(mixinClass.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return simpleName.contains("crystal")
                && containsAny(simpleName, INTERACTION_WORDS)
                && !containsAny(simpleName, READOUT_WORDS);
    }

    private static boolean containsAny(String value, String[] words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for a mod that does something with crystals without optimizing them: Safe Crystal style
     * protections, skins, glows, spins, sounds and counters.
     */
    public static boolean doesThingsWithCrystals(String modId, String modName) {
        String squeezed = ((modId == null ? "" : modId) + " " + (modName == null ? "" : modName))
                .toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-'.]", "");
        // "End crystal" or "crystal": both spellings reach the same words, plurals included.
        squeezed = squeezed.replace("endcrystal", "crystal");
        return containsAny(squeezed, NOT_OPTIMIZER_WORDS);
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

    /**
     * Matches a mod that is a crystal optimizer: a known id, or an id or name that pairs "crystal"
     * with optimizing or client-side handling. A crystal that only looks different, spins faster or
     * is counted is not one.
     */
    public static boolean looksLikeOptimizer(String modId, String modName) {
        String id = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
        if (optimizesTheGame(id)) {
            return false;
        }
        for (String known : KNOWN_OPTIMIZER_IDS) {
            if (id.equals(known)) {
                return true;
            }
        }
        String squeezed = (id + (modName == null ? "" : modName)).toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-'.]", "");
        if (containsAny(squeezed, KNOWN_OPTIMIZER_NAMES)) {
            return true;
        }
        String haystack = (id + " " + (modName == null ? "" : modName)).toLowerCase(Locale.ROOT);
        if (!haystack.contains("crystal")) {
            return false;
        }
        if (haystack.contains("optimi")) {
            return true;
        }
        // A crystal glow or skin may call itself client-side; that alone does not make it an optimizer.
        return !doesThingsWithCrystals(modId, modName) && containsAny(haystack, OPTIMIZER_WORDS);
    }
}
