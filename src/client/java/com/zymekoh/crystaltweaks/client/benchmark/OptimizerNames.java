package com.zymekoh.crystaltweaks.client.benchmark;

import java.util.Locale;

/**
 * Tells a crystal optimizer from any other mod by its id and name, so the benchmark can say which
 * one was handling crystals during a run, and the client-side crystal can stand down for the ones
 * that drive crystals themselves (see {@link #standsDownFor}). Nothing here changes what any other
 * mod does: it only names them.
 */
public final class OptimizerNames {
    private OptimizerNames() {
    }

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
     * The client-side crystal mods, by the ids in their own fabric.mod.json: they draw a stand-in or
     * handle a crystal on this screen. The benchmark names them, and Crystal Tweaks never stands down
     * for them.
     */
    private static final String[] CLIENT_SIDE_IDS = {
            "clientsidecrystals",
            "clientsidedcrystals",
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
     * Whether the client-side crystal stands down for this mod: an optimizer that drives crystals itself,
     * ahead of the server, as Marlow's does, and two of them on the same crystals would fight. Only
     * Crystal Tweaks' crystal optimization stands down for it; its looks, sounds and helpers stay.
     *
     * <p>A client-side crystal mod, a mod that only changes how a crystal looks (colours, glows, skins,
     * spins, sizes, sounds, counters) and a performance mod never count, whatever else their names say.</p>
     */
    public static boolean standsDownFor(String modId, String modName) {
        String id = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
        if (optimizesTheGame(id) || doesThingsWithCrystals(modId, modName) || clientSide(modId, modName)) {
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
        return haystack.contains("crystal") && haystack.contains("optimi");
    }

    /** A client-side crystal mod: a known id, or an id or name that pairs "crystal" with client-side handling. */
    public static boolean clientSide(String modId, String modName) {
        String id = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
        for (String known : CLIENT_SIDE_IDS) {
            if (id.equals(known)) {
                return true;
            }
        }
        String haystack = (id + " " + (modName == null ? "" : modName)).toLowerCase(Locale.ROOT);
        return haystack.contains("crystal") && containsAny(haystack, CLIENT_SIDE_WORDS);
    }

    /** What a client-side crystal mod calls itself. */
    private static final String[] CLIENT_SIDE_WORDS = {
            "client side",
            "clientside",
            "client-side",
            "client_side",
            "client sided",
            "client-sided",
            "clientsided",
    };

    /**
     * Matches a mod that is a crystal optimizer: a known id, or an id or name that pairs "crystal"
     * with optimizing or client-side handling. A crystal that only looks different, spins faster or
     * is counted is not one. This only names it, for the benchmark; {@link #standsDownFor} is what
     * pauses the client-side crystal.
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
