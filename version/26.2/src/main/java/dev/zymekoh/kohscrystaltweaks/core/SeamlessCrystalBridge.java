package dev.zymekoh.kohscrystaltweaks.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

public final class SeamlessCrystalBridge {
    private static final Int2IntOpenHashMap AGE_DELTA = new Int2IntOpenHashMap();
    private static final Int2LongOpenHashMap HIDE_UNTIL_TICK = new Int2LongOpenHashMap();

    private SeamlessCrystalBridge() {
    }

    public static void link(int realEntityId, int ageDelta, int currentTick, int hideTicks) {
        AGE_DELTA.put(realEntityId, ageDelta);
        HIDE_UNTIL_TICK.put(realEntityId, (long) currentTick + hideTicks);
    }

    public static boolean shouldHide(int realEntityId, int currentTick) {
        return currentTick <= HIDE_UNTIL_TICK.getOrDefault(realEntityId, Long.MIN_VALUE);
    }

    public static int ageDeltaFor(int realEntityId) {
        return AGE_DELTA.getOrDefault(realEntityId, 0);
    }

    public static void clear(int realEntityId) {
        AGE_DELTA.remove(realEntityId);
        HIDE_UNTIL_TICK.remove(realEntityId);
    }

    public static void clearAll() {
        AGE_DELTA.clear();
        HIDE_UNTIL_TICK.clear();
    }
}
