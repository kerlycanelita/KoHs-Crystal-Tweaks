package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.compat.ForceCrystalPriorityCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Applies an immediate local selection only after the player explicitly presses a bound hotbar key.
 * It never chooses a slot from an attack/use input and never generates an interaction by itself.
 */
public final class ForceCrystalPvpPriority {
    private ForceCrystalPvpPriority() {
    }

    public static void selectExplicitHotbarSlot(Minecraft minecraft, int selectedSlot) {
        if (!KoHsCrystalTweaksConfig.get().forceCrystalPvpPriorityEnabled
                || !ForceCrystalPriorityCompatibility.isRuntimeAllowed()
                || minecraft.player == null
                || minecraft.player.isSpectator()
                || !Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        Inventory inventory = minecraft.player.getInventory();
        if (!isPriorityStack(inventory.getItem(selectedSlot))) {
            return;
        }
        if (inventory.getSelectedSlot() != selectedSlot) {
            inventory.setSelectedSlot(selectedSlot);
        }
    }

    static boolean isPriorityStack(ItemStack stack) {
        return isPriorityItem(stack.is(Items.END_CRYSTAL), stack.is(Items.OBSIDIAN));
    }

    static boolean isPriorityItem(boolean endCrystal, boolean obsidian) {
        return endCrystal || obsidian;
    }
}
