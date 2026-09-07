package com.zymekoh.crystaltweaks.core;

/**
 * Records whether this build can draw ghost crystals.
 *
 * <p>The renderer needs the level render events introduced with Minecraft 26.1, so it is left out
 * of the 1.x builds. The configuration screen asks this before offering the option, instead of
 * showing a toggle that would do nothing.</p>
 */
public final class GhostCrystalSupport {
    private static volatile boolean available;

    private GhostCrystalSupport() {
    }

    public static void markAvailable() {
        available = true;
    }

    public static boolean isAvailable() {
        return available;
    }
}
