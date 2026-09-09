package com.zymekoh.crystaltweaks.client;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/** Local attribution heuristic, NOT server-provided ownership or a combat target selector. */
public final class CrystalOwnership {
    private static final Map<BlockPos, Long> ATTEMPTS = new HashMap<>();
    private static final Map<EndCrystal, Boolean> OWN = new WeakHashMap<>();
    private static final long WINDOW = 3_000_000_000L;
    private static Object world;

    private CrystalOwnership() { }

    public static void observeWorld(Object currentWorld) {
        if (world != currentWorld) {
            reset();
            world = currentWorld;
        }
    }

    public static void record(BlockPos base, long now) {
        ATTEMPTS.entrySet().removeIf(entry -> now - entry.getValue() > WINDOW);
        if (ATTEMPTS.size() < 128) ATTEMPTS.put(base.immutable(), now);
    }

    public static void loaded(EndCrystal crystal) {
        observeWorld(crystal.level());
        BlockPos base = BlockPos.containing(crystal.getX(), crystal.getY() - 1, crystal.getZ());
        Long sent = ATTEMPTS.remove(base);
        OWN.put(crystal, sent != null && System.nanoTime() - sent <= WINDOW);
    }

    public static boolean useOtherProfile(EndCrystal crystal) {
        return CrystalVisualConfig.enemyCustomEnabled() && !Boolean.TRUE.equals(OWN.get(crystal));
    }

    public static void reset() {
        ATTEMPTS.clear();
        OWN.clear();
        world = null;
    }
}
