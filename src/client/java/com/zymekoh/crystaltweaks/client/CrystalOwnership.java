package com.zymekoh.crystaltweaks.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Local attribution heuristic, NOT server-provided ownership or a combat target selector.
 *
 * <p>Each placement you send is remembered per base, and the crystal that later appears on that base
 * claims the oldest pending attempt. Keeping a queue rather than a single timestamp is what makes
 * this survive spam: place, break, place again on the same obsidian and two crystals arrive against
 * what used to be one record, so the second was read as someone else's and drawn with the enemy
 * profile. The queue gives each arrival its own attempt, in the order they were sent.</p>
 *
 * <p>Nothing here predicts, cancels or sends anything. It only decides which colour profile the
 * renderer uses, which is why it keeps running while another optimizer owns the interaction path.</p>
 */
public final class CrystalOwnership {
    /** How long a placement stays claimable. Comfortably past any round trip worth colouring. */
    private static final long WINDOW = 3_000_000_000L;

    /** Distinct bases remembered at once. Oldest base is evicted first when spam exceeds this. */
    private static final int MAX_BASES = 192;

    /** Pending attempts kept for one base. Deeper than any realistic place/break/place burst. */
    private static final int MAX_PER_BASE = 8;

    // Access order is insertion order here, which is what the eviction below wants: the base whose
    // first attempt is oldest leaves first.
    private static final Map<BlockPos, Deque<Long>> ATTEMPTS = new LinkedHashMap<>();
    private static final Map<EndCrystal, Boolean> OWN = new WeakHashMap<>();
    private static Object world;

    private CrystalOwnership() { }

    public static void observeWorld(Object currentWorld) {
        if (world != currentWorld) {
            reset();
            world = currentWorld;
        }
    }

    public static void record(BlockPos base, long now) {
        expire(now);
        BlockPos key = base.immutable();
        Deque<Long> pending = ATTEMPTS.get(key);
        if (pending == null) {
            while (ATTEMPTS.size() >= MAX_BASES) {
                ATTEMPTS.remove(ATTEMPTS.keySet().iterator().next());
            }
            pending = new ArrayDeque<>(4);
            ATTEMPTS.put(key, pending);
        }
        // A burst deeper than the cap means the earliest attempts already timed out in practice;
        // dropping the oldest keeps the newest claimable instead of refusing to record at all.
        while (pending.size() >= MAX_PER_BASE) {
            pending.pollFirst();
        }
        pending.addLast(now);
    }

    public static void loaded(EndCrystal crystal) {
        observeWorld(crystal.level());
        BlockPos base = BlockPos.containing(crystal.getX(), crystal.getY() - 1, crystal.getZ());
        OWN.put(crystal, claim(base, System.nanoTime()));
    }

    public static boolean useOtherProfile(EndCrystal crystal) {
        return CrystalVisualConfig.enemyCustomEnabled() && !Boolean.TRUE.equals(OWN.get(crystal));
    }

    public static void reset() {
        ATTEMPTS.clear();
        OWN.clear();
        world = null;
    }

    /** Consumes the oldest attempt still inside the window on this base. */
    private static boolean claim(BlockPos base, long now) {
        Deque<Long> pending = ATTEMPTS.get(base);
        if (pending == null) {
            return false;
        }
        while (true) {
            Long sent = pending.pollFirst();
            if (sent == null) {
                ATTEMPTS.remove(base);
                return false;
            }
            if (now - sent <= WINDOW) {
                if (pending.isEmpty()) {
                    ATTEMPTS.remove(base);
                }
                return true;
            }
        }
    }

    private static void expire(long now) {
        ATTEMPTS.values().forEach(pending -> {
            while (!pending.isEmpty() && now - pending.peekFirst() > WINDOW) {
                pending.pollFirst();
            }
        });
        ATTEMPTS.values().removeIf(Deque::isEmpty);
    }
}
