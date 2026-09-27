package com.zymekoh.crystaltweaks.practice;

import java.util.Arrays;

/**
 * Where every item of a practice kit goes: the nine hotbar slots, the 27 of the inventory and the
 * off hand, numbered as Minecraft numbers a player's inventory (0-8 hotbar, 9-35 inventory, 40 off
 * hand). Armour is not part of it: it is always worn.
 *
 * <p>The totem count is not stored apart from the layout. It is simply how many totems the layout
 * holds, so the kit editor, the totem selector and the bot, which carries as many as the player,
 * can never disagree about it.</p>
 */
public final class KitLayout {
    public static final int HOTBAR = 9;
    public static final int INVENTORY = 36;
    public static final int OFFHAND = 40;
    public static final int SLOTS = 41;
    /** Totem count meaning "every slot the kit leaves empty". */
    public static final int FULL = -1;
    /** The steps the totem selector walks through. */
    public static final int[] TOTEM_STEPS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, FULL};

    /** One slot's content. */
    public record Entry(KitItem item, int count) {
        public Entry {
            if (item == null) {
                throw new IllegalArgumentException("item");
            }
            count = Math.max(1, Math.min(item.maxStack, count));
        }

        public static Entry full(KitItem item) {
            return new Entry(item, item.maxStack);
        }
    }

    private final Entry[] slots = new Entry[SLOTS];

    public KitLayout() {
    }

    /** True for the slots a kit may use: the 36 of the inventory and the off hand. */
    public static boolean usable(int slot) {
        return (slot >= 0 && slot < INVENTORY) || slot == OFFHAND;
    }

    public Entry get(int slot) {
        return usable(slot) ? this.slots[slot] : null;
    }

    public KitLayout set(int slot, Entry entry) {
        if (usable(slot)) {
            this.slots[slot] = entry;
        }
        return this;
    }

    public KitLayout put(int slot, KitItem item) {
        return set(slot, Entry.full(item));
    }

    public void swap(int first, int second) {
        if (!usable(first) || !usable(second)) {
            return;
        }
        Entry held = this.slots[first];
        this.slots[first] = this.slots[second];
        this.slots[second] = held;
    }

    public KitLayout copy() {
        KitLayout copy = new KitLayout();
        System.arraycopy(this.slots, 0, copy.slots, 0, SLOTS);
        return copy;
    }

    /** Every item of this kind, across all its stacks. */
    public int count(KitItem item) {
        int total = 0;
        for (Entry entry : this.slots) {
            if (entry != null && entry.item() == item) {
                total += entry.count();
            }
        }
        return total;
    }

    public int totems() {
        return count(KitItem.TOTEM);
    }

    public boolean isEmpty() {
        for (Entry entry : this.slots) {
            if (entry != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * A copy holding exactly {@code target} totems, or one in every empty slot for {@link #FULL}.
     *
     * <p>New totems go to the off hand first, then the hotbar from its right end, where players keep
     * the one they swap to, then the inventory. Extra ones leave in the opposite order, so the off
     * hand keeps its totem as long as there is one.</p>
     */
    public KitLayout withTotems(int target) {
        KitLayout copy = copy();
        int[] order = totemOrder();
        if (target == FULL) {
            for (int slot : order) {
                if (copy.slots[slot] == null) {
                    copy.slots[slot] = Entry.full(KitItem.TOTEM);
                }
            }
            return copy;
        }
        int wanted = Math.max(1, Math.min(order.length, target));
        int current = copy.totems();
        for (int index = 0; index < order.length && current < wanted; index++) {
            int slot = order[index];
            if (copy.slots[slot] == null) {
                copy.slots[slot] = Entry.full(KitItem.TOTEM);
                current++;
            }
        }
        for (int index = order.length - 1; index >= 0 && current > wanted; index--) {
            int slot = order[index];
            Entry entry = copy.slots[slot];
            if (entry != null && entry.item() == KitItem.TOTEM) {
                copy.slots[slot] = null;
                current--;
            }
        }
        return copy;
    }

    /** True when no slot is left for another totem, which the selector shows as "full". */
    public boolean full() {
        for (int slot : totemOrder()) {
            if (this.slots[slot] == null) {
                return false;
            }
        }
        return true;
    }

    /** The totem selector's step after (or before) the current count. */
    public static int nextTotemStep(KitLayout layout, boolean forward) {
        int current = layout.full() ? FULL : layout.totems();
        if (forward) {
            if (current == FULL) {
                return TOTEM_STEPS[0];
            }
            for (int step : TOTEM_STEPS) {
                if (step == FULL || step > current) {
                    return step;
                }
            }
            return FULL;
        }
        if (current == FULL) {
            return TOTEM_STEPS[TOTEM_STEPS.length - 2];
        }
        for (int index = TOTEM_STEPS.length - 2; index >= 0; index--) {
            if (TOTEM_STEPS[index] < current) {
                return TOTEM_STEPS[index];
            }
        }
        return FULL;
    }

    private static int[] totemOrder() {
        int[] order = new int[INVENTORY + 1];
        int index = 0;
        order[index++] = OFFHAND;
        for (int slot = HOTBAR - 1; slot >= 0; slot--) {
            order[index++] = slot;
        }
        for (int slot = HOTBAR; slot < INVENTORY; slot++) {
            order[index++] = slot;
        }
        return order;
    }

    /** Compact text for the configuration file: {@code slot:item:count} entries separated by commas. */
    public String encode() {
        StringBuilder text = new StringBuilder();
        for (int slot = 0; slot < SLOTS; slot++) {
            Entry entry = this.slots[slot];
            if (entry == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(slot).append(':').append(entry.item().id).append(':').append(entry.count());
        }
        return text.toString();
    }

    /**
     * Reads {@link #encode()}'s text. A hand-edited file is read as far as it makes sense: unknown
     * items, slots outside the inventory and repeated slots are skipped rather than failing the kit.
     *
     * @return the layout, or {@code null} when nothing in the text was usable
     */
    public static KitLayout decode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        KitLayout layout = new KitLayout();
        for (String part : text.split(",")) {
            String[] fields = part.trim().split(":");
            if (fields.length != 3) {
                continue;
            }
            try {
                int slot = Integer.parseInt(fields[0].trim());
                KitItem item = KitItem.parse(fields[1].trim());
                int count = Integer.parseInt(fields[2].trim());
                if (item != null && usable(slot) && layout.slots[slot] == null && count > 0) {
                    layout.slots[slot] = new Entry(item, count);
                }
            } catch (NumberFormatException ignored) {
                // A damaged entry is skipped; the rest of the kit still loads.
            }
        }
        return layout.isEmpty() ? null : layout;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KitLayout layout && Arrays.equals(this.slots, layout.slots);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.slots);
    }
}
