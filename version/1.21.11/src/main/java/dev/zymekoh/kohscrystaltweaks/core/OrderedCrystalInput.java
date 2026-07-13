package dev.zymekoh.kohscrystaltweaks.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Preserves the arrival order of vanilla attack and use presses until the next
 * client input pass. The queue never creates an action: consumers must match
 * every entry against the corresponding vanilla {@code KeyBinding.wasPressed()}
 * counter before executing it.
 */
public final class OrderedCrystalInput {
    private static final int MAX_QUEUED_INPUTS = 1024;
    private static final ArrayDeque<Action> QUEUE = new ArrayDeque<>();

    private static boolean vanillaFallback;

    private OrderedCrystalInput() {
    }

    public static synchronized void record(boolean attack, boolean use) {
        if (!CrystalPlacementFix.isEnabled() || vanillaFallback || (!attack && !use)) {
            return;
        }

        int additions = (attack ? 1 : 0) + (use ? 1 : 0);
        if (QUEUE.size() + additions > MAX_QUEUED_INPUTS) {
            QUEUE.clear();
            vanillaFallback = true;
            return;
        }

        // This is also vanilla's deterministic order when one physical input is
        // intentionally bound to both actions.
        if (attack) {
            QUEUE.addLast(Action.ATTACK);
        }
        if (use) {
            QUEUE.addLast(Action.USE);
        }
    }

    public static synchronized List<Action> drain() {
        if (vanillaFallback) {
            vanillaFallback = false;
            QUEUE.clear();
            return List.of();
        }

        List<Action> ordered = new ArrayList<>(QUEUE);
        QUEUE.clear();
        return ordered;
    }

    public static synchronized void clear() {
        QUEUE.clear();
        vanillaFallback = false;
    }

    public enum Action {
        ATTACK,
        USE
    }
}
