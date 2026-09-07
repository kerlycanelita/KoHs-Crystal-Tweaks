package com.zymekoh.crystaltweaks.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/**
 * The placements this client is still waiting on, so a stand-in crystal can be drawn over the gap.
 *
 * <p>These are positions, not entities. Nothing is ever added to the level, so a ghost cannot be
 * looked at, attacked, collided with or counted by any Vanilla check, and no packet can mention it.
 * The moment the server's crystal arrives the entry is dropped and the real entity takes over.</p>
 */
public final class GhostCrystalTracker {
    private static final int MAX_PENDING = 16;

    private static final Map<BlockPos, Long> PENDING = new ConcurrentHashMap<>();

    private GhostCrystalTracker() {
    }

    public static void add(BlockPos base, long nowNanos) {
        if (PENDING.size() >= MAX_PENDING) {
            cleanup(nowNanos);
            if (PENDING.size() >= MAX_PENDING) {
                return;
            }
        }
        PENDING.put(base.immutable(), nowNanos + CrystalBreakPrediction.predictionWindowNanos());
    }

    /** The server's crystal arrived, so the stand-in is no longer needed. */
    public static void confirm(BlockPos base) {
        PENDING.remove(base);
    }

    public static boolean isEmpty() {
        return PENDING.isEmpty();
    }

    public static void forEachPending(Consumer<BlockPos> action) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (Map.Entry<BlockPos, Long> entry : PENDING.entrySet()) {
            if (now >= entry.getValue()) {
                PENDING.remove(entry.getKey());
                continue;
            }
            action.accept(entry.getKey());
        }
    }

    public static void cleanup(long nowNanos) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.values().removeIf(deadline -> nowNanos >= deadline);
    }

    public static void reset() {
        PENDING.clear();
    }
}
