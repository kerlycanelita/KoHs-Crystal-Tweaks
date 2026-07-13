package dev.zymekoh.kohscrystaltweaks.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;

/**
 * Preserves physical hotbar, attack, and use order until Minecraft's next input pass.
 * Every attack/use entry must still consume its matching vanilla key counter.
 */
public final class OrderedCrystalInput {
    private static final int MAX_QUEUED_INPUTS = 1024;
    private static final int NO_SLOT = -1;
    private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>();

    private static int initialSlot = NO_SLOT;
    private static boolean vanillaFallback;

    private OrderedCrystalInput() {
    }

    public static synchronized void recordInput(boolean attack, boolean use, int selectedSlot) {
        if (!CrystalPlacementFix.isEnabled() || vanillaFallback || (!attack && !use)) {
            return;
        }
        recordInputUnchecked(attack, use, selectedSlot);
    }

    public static synchronized void recordSlotChange(int previousSlot, int selectedSlot) {
        if (!CrystalPlacementFix.isEnabled() || vanillaFallback) {
            return;
        }
        recordSlotChangeUnchecked(previousSlot, selectedSlot);
    }

    static void recordInputUnchecked(boolean attack, boolean use, int selectedSlot) {
        if (!attack && !use) {
            return;
        }
        beginSequence(selectedSlot);
        int additions = (attack ? 1 : 0) + (use ? 1 : 0);
        if (!reserve(additions)) {
            return;
        }

        // Matches vanilla's deterministic order when one physical input is bound
        // to both actions.
        if (attack) {
            QUEUE.addLast(new Entry(Action.ATTACK, NO_SLOT));
        }
        if (use) {
            QUEUE.addLast(new Entry(Action.USE, NO_SLOT));
        }
    }

    static void recordSlotChangeUnchecked(int previousSlot, int selectedSlot) {
        if (!PlayerInventory.isValidHotbarIndex(previousSlot)
                || !PlayerInventory.isValidHotbarIndex(selectedSlot)
                || previousSlot == selectedSlot) {
            return;
        }
        beginSequence(previousSlot);
        if (reserve(1)) {
            QUEUE.addLast(new Entry(Action.SELECT_SLOT, selectedSlot));
        }
    }

    public static synchronized Batch drain() {
        if (vanillaFallback) {
            clearInternal();
            return Batch.EMPTY;
        }

        Batch batch = QUEUE.isEmpty()
                ? Batch.EMPTY
                : new Batch(initialSlot, List.copyOf(new ArrayList<>(QUEUE)));
        clearInternal();
        return batch;
    }

    public static synchronized void clear() {
        clearInternal();
    }

    private static void beginSequence(int selectedSlot) {
        if (initialSlot == NO_SLOT && PlayerInventory.isValidHotbarIndex(selectedSlot)) {
            initialSlot = selectedSlot;
        }
    }

    private static boolean reserve(int additions) {
        if (QUEUE.size() + additions <= MAX_QUEUED_INPUTS) {
            return true;
        }
        QUEUE.clear();
        initialSlot = NO_SLOT;
        vanillaFallback = true;
        return false;
    }

    private static void clearInternal() {
        QUEUE.clear();
        initialSlot = NO_SLOT;
        vanillaFallback = false;
    }

    public enum Action {
        SELECT_SLOT,
        ATTACK,
        USE
    }

    public record Entry(Action action, int selectedSlot) {
    }

    public record Batch(int initialSlot, List<Entry> entries) {
        private static final Batch EMPTY = new Batch(NO_SLOT, List.of());

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }
}
