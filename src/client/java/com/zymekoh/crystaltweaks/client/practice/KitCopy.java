package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Your server inventory as a practice kit: every item a kit can hold, in the slot it is in, as many
 * as you carry. What a kit cannot hold is left out, and the armour is the one chosen in the kit's
 * settings. Read from your own client; nothing is sent.
 */
final class KitCopy {
    private KitCopy() {
    }

    /** The server player to copy from: the one parked behind practice, or yours while on a server. Null otherwise. */
    static LocalPlayer source(Minecraft minecraft, boolean inWorld) {
        if (PracticeHost.parked()) {
            return PracticeHost.serverPlayer();
        }
        return !inWorld && minecraft.player != null && minecraft.getSingleplayerServer() == null ? minecraft.player : null;
    }

    static KitLayout from(Inventory inventory) {
        KitLayout layout = new KitLayout();
        for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
            if (!KitLayout.usable(slot)) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            KitItem item = kitItem(stack);
            if (item != null) {
                layout.set(slot, new KitLayout.Entry(item, stack.getCount()));
            }
        }
        return layout;
    }

    /** How many stacks a kit cannot hold were left out. */
    static int leftOut(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (KitLayout.usable(slot) && !stack.isEmpty() && kitItem(stack) == null) {
                count++;
            }
        }
        return count;
    }

    private static KitItem kitItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.END_CRYSTAL)) {
            return KitItem.END_CRYSTAL;
        }
        if (stack.is(Items.OBSIDIAN)) {
            return KitItem.OBSIDIAN;
        }
        if (stack.is(Items.RESPAWN_ANCHOR)) {
            return KitItem.RESPAWN_ANCHOR;
        }
        if (stack.is(Items.GLOWSTONE)) {
            return KitItem.GLOWSTONE;
        }
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return KitItem.TOTEM;
        }
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return KitItem.GOLDEN_APPLE;
        }
        if (stack.is(Items.ENDER_PEARL)) {
            return KitItem.ENDER_PEARL;
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return KitItem.EXPERIENCE_BOTTLE;
        }
        if (stack.is(ItemTags.SWORDS)) {
            return KitItem.SWORD;
        }
        if (stack.is(ItemTags.PICKAXES)) {
            return KitItem.PICKAXE;
        }
        return null;
    }
}
