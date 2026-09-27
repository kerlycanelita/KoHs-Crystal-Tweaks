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
 * Protection IV instead of Protection, since the two cannot share a piece. The sword carries
 * Sharpness V and the chosen Knockback. The bot is dressed and armed from the very same methods, so
 * its gear always matches the player's.</p>
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
        // Knockback I is what crystal PvP kits carry: enough to lift a player into a hit-crystal.
        sword.enchant(enchantment(enchantments, Enchantments.KNOCKBACK), settings.knockback);
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

    /** The kit's pickaxe, for the bot mining its way out. */
    public static ItemStack pickaxeFor(ServerLevel level, PracticeSettings settings) {
        return pickaxe(level, settings);
    }

    /** The mining speed of the kit's pickaxe before Efficiency: netherite 9, diamond 8, iron 6. */
    public static float pickaxeSpeed(PracticeSettings settings) {
        return switch (settings.armor) {
            case NETHERITE -> 9.0F;
            case DIAMOND -> 8.0F;
            case IRON -> 6.0F;
        };
    }

    /** The real item stack for one slot of a kit. */
    public static ItemStack stack(ServerLevel level, PracticeSettings settings, KitLayout.Entry entry) {
        return switch (entry.item()) {
            case SWORD -> sword(level, settings);
            case PICKAXE -> pickaxe(level, settings);
            case END_CRYSTAL -> new ItemStack(Items.END_CRYSTAL, entry.count());
            case OBSIDIAN -> new ItemStack(Items.OBSIDIAN, entry.count());
            case RESPAWN_ANCHOR -> new ItemStack(Items.RESPAWN_ANCHOR, entry.count());
            case GLOWSTONE -> new ItemStack(Items.GLOWSTONE, entry.count());
            case TOTEM -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case GOLDEN_APPLE -> new ItemStack(Items.GOLDEN_APPLE, entry.count());
            case ENDER_PEARL -> new ItemStack(Items.ENDER_PEARL, entry.count());
            case EXPERIENCE_BOTTLE -> new ItemStack(Items.EXPERIENCE_BOTTLE, entry.count());
        };
    }

    private static Item item(KitItem kind) {
        return switch (kind) {
            case SWORD -> Items.NETHERITE_SWORD;
            case PICKAXE -> Items.NETHERITE_PICKAXE;
            case END_CRYSTAL -> Items.END_CRYSTAL;
            case OBSIDIAN -> Items.OBSIDIAN;
            case RESPAWN_ANCHOR -> Items.RESPAWN_ANCHOR;
            case GLOWSTONE -> Items.GLOWSTONE;
            case TOTEM -> Items.TOTEM_OF_UNDYING;
            case GOLDEN_APPLE -> Items.GOLDEN_APPLE;
            case ENDER_PEARL -> Items.ENDER_PEARL;
            case EXPERIENCE_BOTTLE -> Items.EXPERIENCE_BOTTLE;
        };
    }

    /**
     * Gives the player the kit exactly as laid out in the kit editor: every stack in its slot, the
     * off hand's included, and the armour worn.
     */
    public static void equipPlayer(ServerPlayer player, PracticeSettings settings, KitLayout layout) {
        ServerLevel level = (ServerLevel) player.level();
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        dress(player, level, settings);
        int selected = 0;
        for (int slot = 0; slot < KitLayout.INVENTORY; slot++) {
            KitLayout.Entry entry = layout.get(slot);
            if (entry != null) {
                inventory.setItem(slot, stack(level, settings, entry));
                if (slot < KitLayout.HOTBAR && entry.item() == KitItem.SWORD) {
                    selected = slot;
                }
            }
        }
        KitLayout.Entry offhand = layout.get(KitLayout.OFFHAND);
        player.setItemSlot(EquipmentSlot.OFFHAND, offhand == null ? ItemStack.EMPTY : stack(level, settings, offhand));
        inventory.setSelectedSlot(selected);
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Tops crystals, obsidian, anchors and glowstone back up during practice, into the slots the kit
     * editor gave them, so a long fight never ends because a stack ran out and the hotbar stays where
     * the hands expect it. Totems, apples and pearls are left alone: running out is part of it.
     */
    public static void restock(ServerPlayer player, KitLayout layout) {
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (KitItem kind : KitItem.values()) {
            if (!kind.restocks()) {
                continue;
            }
            Item item = item(kind);
            int missing = layout.count(kind) - inventory.countItem(item);
            for (int slot = 0; slot < KitLayout.INVENTORY && missing > 0; slot++) {
                KitLayout.Entry entry = layout.get(slot);
                if (entry == null || entry.item() != kind) {
                    continue;
                }
                ItemStack current = inventory.getItem(slot);
                if (current.isEmpty()) {
                    int amount = Math.min(entry.count(), missing);
                    inventory.setItem(slot, new ItemStack(item, amount));
                    missing -= amount;
                    changed = true;
                } else if (current.is(item) && current.getCount() < entry.count()) {
                    int amount = Math.min(entry.count() - current.getCount(), missing);
                    current.grow(amount);
                    missing -= amount;
                    changed = true;
                }
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
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
