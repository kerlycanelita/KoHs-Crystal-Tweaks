package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmark;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import java.util.List;

/**
 * Whether each part of the client-side crystal runs right now: switched on by the player, never for
 * the server parked behind Crystal Practice, whose packets are handled while another world is on
 * screen and whose entity ids mean nothing in it, and never next to a crystal optimizer such as
 * Marlow's, which would be driving the same crystals.
 *
 * <p>Only this, the crystal optimization, stands down. The colours, glow, flash, sounds, helpers,
 * fight tools and Crystal Practice run whatever else is installed, and a client-side crystal mod or
 * a mod that changes how crystals look never stands anything down (see
 * {@link com.zymekoh.crystaltweaks.client.benchmark.OptimizerNames#standsDownFor}).</p>
 */
public final class CrystalCore {
    private static List<String> others;

    private CrystalCore() {
    }

    /** The break on screen at the hit, and the removal reader. */
    public static boolean active() {
        return CrystalVisualConfig.clientCrystal() && !PracticeHost.serverSide() && otherOptimizers().isEmpty();
    }

    public static boolean explosions() {
        return active() && CrystalVisualConfig.instantExplosion();
    }

    public static boolean ghosts() {
        return active() && CrystalVisualConfig.ghostCrystals();
    }

    public static boolean fastPlacement() {
        return active() && CrystalVisualConfig.fastPlacement();
    }

    /** The crystal optimizers installed beside Crystal Tweaks; the client-side crystal stands down for them. */
    public static List<String> otherOptimizers() {
        List<String> found = others;
        if (found == null) {
            // The mod list cannot change while the game runs.
            found = CrystalBenchmark.standDownMods();
            others = found;
        }
        return found;
    }
}
