package com.zymekoh.crystaltweaks.practice;

import java.util.Locale;

/**
 * What a Crystal Practice kit can hold. Plain Java with no Minecraft types, so the kit editor, the
 * stored layouts and the tests all share it; {@link PracticeKit} turns each one into a real item
 * stack on the practice world's server.
 */
public enum KitItem {
    SWORD("sword", 1, "Espada", "Sword"),
    PICKAXE("pickaxe", 1, "Pico", "Pickaxe"),
    END_CRYSTAL("end_crystal", 64, "Cristal del End", "End Crystal"),
    OBSIDIAN("obsidian", 64, "Obsidiana", "Obsidian"),
    RESPAWN_ANCHOR("respawn_anchor", 64, "Ancla de reaparición", "Respawn Anchor"),
    GLOWSTONE("glowstone", 64, "Piedra luminosa", "Glowstone"),
    TOTEM("totem", 1, "Tótem de la inmortalidad", "Totem of Undying"),
    GOLDEN_APPLE("golden_apple", 64, "Manzana dorada", "Golden Apple"),
    ENDER_PEARL("ender_pearl", 16, "Perla de ender", "Ender Pearl"),
    EXPERIENCE_BOTTLE("experience_bottle", 64, "Botella de experiencia", "Bottle o' Enchanting");

    public final String id;
    public final int maxStack;
    private final String spanish;
    private final String english;

    KitItem(String id, int maxStack, String spanish, String english) {
        this.id = id;
        this.maxStack = maxStack;
        this.spanish = spanish;
        this.english = english;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    /** Topped back up during practice: running out of these would end a fight for no good reason. */
    public boolean restocks() {
        return this == END_CRYSTAL || this == OBSIDIAN || this == RESPAWN_ANCHOR || this == GLOWSTONE;
    }

    /** Carries the kit's enchantments, which the editor shows with a glint. */
    public boolean enchanted() {
        return this == SWORD || this == PICKAXE;
    }

    /**
     * The texture the kit editor draws for this item, under {@code textures/}. Tools follow the
     * chosen armour material, as the kit itself does.
     */
    public String texture(PracticeSettings.Armor armor) {
        String material = armor.name().toLowerCase(Locale.ROOT);
        return switch (this) {
            case SWORD -> "item/" + material + "_sword";
            case PICKAXE -> "item/" + material + "_pickaxe";
            case END_CRYSTAL -> "item/end_crystal";
            case OBSIDIAN -> "block/obsidian";
            case RESPAWN_ANCHOR -> "block/respawn_anchor_side0";
            case GLOWSTONE -> "block/glowstone";
            case TOTEM -> "item/totem_of_undying";
            case GOLDEN_APPLE -> "item/golden_apple";
            case ENDER_PEARL -> "item/ender_pearl";
            case EXPERIENCE_BOTTLE -> "item/experience_bottle";
        };
    }

    static KitItem parse(String value) {
        for (KitItem item : values()) {
            if (item.id.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        return null;
    }
}
