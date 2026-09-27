package com.zymekoh.crystaltweaks.practice;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The gear for Crystal Practice, built on the integrated server of the practice world only.
 *
 * <p>Every armour piece carries Mending, Unbreaking III and Protection IV; up to two carry Blast
 * Protection IV instead of Protection, since the two cannot share a piece. The bot is dressed from
 * the very same method, so its armour always matches the player's.</p>
 */
public final class PracticeKit {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int[] SLOT_BITS = {
            PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET
    };

    private PracticeKit() {
    }

    /** The armour piece for {@code slot}, enchanted as the settings say. */
    public static ItemStack armorPiece(ServerLevel level, PracticeSettings settings, EquipmentSlot slot) {
        int index = slotIndex(slot);
        ItemStack stack = new ItemStack(armorItem(settings.armor, slot));
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean blast = settings.blast(SLOT_BITS[index]);
        stack.enchant(enchantment(enchantments, blast ? Enchantments.BLAST_PROTECTION : Enchantments.PROTECTION), 4);
        stack.enchant(enchantment(enchantments, Enchantments.UNBREAKING), 3);
        stack.enchant(enchantment(enchantments, Enchantments.MENDING), 1);
        return stack;
    }

    /** Dresses a player or the bot in the chosen armour. */
    public static void dress(LivingEntity entity, ServerLevel level, PracticeSettings settings) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            entity.setItemSlot(slot, armorPiece(level, settings, slot));
        }
    }

    public static ItemStack sword(ServerLevel level, PracticeSettings settings) {
        Item item = switch (settings.armor) {
            case NETHERITE -> Items.NETHERITE_SWORD;
            case DIAMOND -> Items.DIAMOND_SWORD;
            case IRON -> Items.IRON_SWORD;
        };
        ItemStack sword = new ItemStack(item);
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        sword.enchant(enchantment(enchantments, Enchantments.SHARPNESS), 5);
        sword.enchant(enchantment(enchantments, Enchantments.UNBREAKING), 3);
        sword.enchant(enchantment(enchantments, Enchantments.MENDING), 1);
        return sword;
    }

    private static ItemStack pickaxe(ServerLevel level, PracticeSettings settings) {
        Item item = switch (settings.armor) {
            case NETHERITE -> Items.NETHERITE_PICKAXE;
            case DIAMOND -> Items.DIAMOND_PICKAXE;
            case IRON -> Items.IRON_PICKAXE;
        };
        ItemStack pickaxe = new ItemStack(item);
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        pickaxe.enchant(enchantment(enchantments, Enchantments.EFFICIENCY), 5);
        pickaxe.enchant(enchantment(enchantments, Enchantments.UNBREAKING), 3);
        pickaxe.enchant(enchantment(enchantments, Enchantments.MENDING), 1);
        return pickaxe;
    }

    /**
     * A full crystal PvP loadout: armour, sword, crystals, obsidian, totems, golden apples, pearls,
     * a pickaxe and experience bottles to mend with.
     */
    public static void equipPlayer(ServerPlayer player, PracticeSettings settings) {
        ServerLevel level = (ServerLevel) player.level();
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        dress(player, level, settings);
        inventory.setItem(0, sword(level, settings));
        inventory.setItem(1, new ItemStack(Items.END_CRYSTAL, 64));
        inventory.setItem(2, new ItemStack(Items.OBSIDIAN, 64));
        inventory.setItem(3, new ItemStack(Items.GOLDEN_APPLE, 64));
        inventory.setItem(4, new ItemStack(Items.TOTEM_OF_UNDYING));
        inventory.setItem(5, new ItemStack(Items.ENDER_PEARL, 16));
        inventory.setItem(6, pickaxe(level, settings));
        inventory.setItem(7, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        inventory.setItem(8, new ItemStack(Items.END_CRYSTAL, 64));
        for (int slot = 9; slot < 18; slot++) {
            inventory.setItem(slot, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        for (int slot = 18; slot < 24; slot++) {
            inventory.setItem(slot, new ItemStack(Items.END_CRYSTAL, 64));
        }
        for (int slot = 24; slot < 30; slot++) {
            inventory.setItem(slot, new ItemStack(Items.OBSIDIAN, 64));
        }
        inventory.setItem(30, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        inventory.setItem(31, new ItemStack(Items.GOLDEN_APPLE, 64));
        inventory.setItem(32, new ItemStack(Items.ENDER_PEARL, 16));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        inventory.setSelectedSlot(1);
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Tops the crystals and obsidian back up during practice, so a long fight never ends because a
     * stack ran out; totems and apples are left alone, running out of those is part of it.
     */
    public static void restock(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        topUp(inventory, Items.END_CRYSTAL, 64 * 3);
        topUp(inventory, Items.OBSIDIAN, 64 * 3);
    }

    private static void topUp(Inventory inventory, Item item, int minimum) {
        int count = inventory.countItem(item);
        if (count >= minimum) {
            return;
        }
        int missing = minimum - count;
        for (int slot = 9; slot < 36 && missing > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                int amount = Math.min(64, missing);
                inventory.setItem(slot, new ItemStack(item, amount));
                missing -= amount;
            } else if (stack.is(item) && stack.getCount() < 64) {
                int amount = Math.min(64 - stack.getCount(), missing);
                stack.grow(amount);
                missing -= amount;
            }
        }
    }

    private static Item armorItem(PracticeSettings.Armor armor, EquipmentSlot slot) {
        return switch (armor) {
            case NETHERITE -> switch (slotIndex(slot)) {
                case 0 -> Items.NETHERITE_HELMET;
                case 1 -> Items.NETHERITE_CHESTPLATE;
                case 2 -> Items.NETHERITE_LEGGINGS;
                default -> Items.NETHERITE_BOOTS;
            };
            case DIAMOND -> switch (slotIndex(slot)) {
                case 0 -> Items.DIAMOND_HELMET;
                case 1 -> Items.DIAMOND_CHESTPLATE;
                case 2 -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            };
            case IRON -> switch (slotIndex(slot)) {
                case 0 -> Items.IRON_HELMET;
                case 1 -> Items.IRON_CHESTPLATE;
                case 2 -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            };
        };
    }

    private static int slotIndex(EquipmentSlot slot) {
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            if (ARMOR_SLOTS[index] == slot) {
                return index;
            }
        }
        return 3;
    }

    private static Holder<Enchantment> enchantment(HolderLookup.RegistryLookup<Enchantment> lookup, ResourceKey<Enchantment> key) {
        return lookup.getOrThrow(key);
    }
}
