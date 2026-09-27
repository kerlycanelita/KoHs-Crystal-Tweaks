package com.zymekoh.crystaltweaks.practice;

import static com.zymekoh.crystaltweaks.practice.KitItem.END_CRYSTAL;
import static com.zymekoh.crystaltweaks.practice.KitItem.ENDER_PEARL;
import static com.zymekoh.crystaltweaks.practice.KitItem.EXPERIENCE_BOTTLE;
import static com.zymekoh.crystaltweaks.practice.KitItem.GLOWSTONE;
import static com.zymekoh.crystaltweaks.practice.KitItem.GOLDEN_APPLE;
import static com.zymekoh.crystaltweaks.practice.KitItem.OBSIDIAN;
import static com.zymekoh.crystaltweaks.practice.KitItem.PICKAXE;
import static com.zymekoh.crystaltweaks.practice.KitItem.RESPAWN_ANCHOR;
import static com.zymekoh.crystaltweaks.practice.KitItem.SWORD;
import static com.zymekoh.crystaltweaks.practice.KitItem.TOTEM;

/**
 * Ready-made kits, each a starting point the player can rearrange in the kit editor and save.
 *
 * <p>None of the ladders publishes its crystal kit item by item: MCTiers asks players to bring
 * their own, and MCPVP and PVPHQ build theirs inside their servers' kit editors. So these follow
 * what each one does publish (MCTiers' "Bring your own", MCPVP's End Game tier with several totems,
 * PVPHQ's "end crystals and anchors") and say so where the player picks them, rather than passing
 * a guess off as an official kit.</p>
 */
public enum KitPreset {
    STANDARD("standard", "CPvP estándar", "CPvP standard",
            "El kit clásico de la comunidad: 10 tótems, dos stacks de cristales y de obsidiana, anclas, perlas y manzanas.",
            "The community's classic kit: 10 totems, two stacks of crystals and obsidian, anchors, pearls and apples."),
    MCTIERS("mctiers", "Estilo MCTiers", "MCTiers style",
            "MCTiers Vanilla no reparte kit: cada jugador trae el suyo. Este es uno completo, con el inventario lleno de tótems.",
            "MCTiers Vanilla hands out no kit: every player brings their own. This is a full one, the inventory filled with totems."),
    MCPVP("mcpvp", "Estilo MCPVP", "MCPVP style",
            "Inspirado en el kit End Game de MCPVP: netherite, varios tótems y recursos avanzados. El reparto exacto vive en su editor de kits.",
            "Modelled on MCPVP's End Game kit: netherite, several totems and advanced resources. Its exact layout lives in their kit editor."),
    PVPHQ("pvphq", "Estilo PVPHQ", "PVPHQ style",
            "Inspirado en la cola Vanilla de PVPHQ, cristales y anclas: más anclas y piedra luminosa a mano.",
            "Modelled on PVPHQ's Vanilla queue, crystals and anchors: more anchors and glowstone at hand."),
    LIGHT("light", "Ligero", "Light",
            "Solo 3 tótems: para practicar a cerrar peleas sin margen de error.",
            "Only 3 totems: for practising closing out fights with no room for error.");

    public final String id;
    private final String spanish;
    private final String english;
    private final String spanishNote;
    private final String englishNote;

    KitPreset(String id, String spanish, String english, String spanishNote, String englishNote) {
        this.id = id;
        this.spanish = spanish;
        this.english = english;
        this.spanishNote = spanishNote;
        this.englishNote = englishNote;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    public String note(boolean useSpanish) {
        return useSpanish ? this.spanishNote : this.englishNote;
    }

    public KitPreset next() {
        KitPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public KitPreset previous() {
        KitPreset[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static KitPreset parse(String value) {
        for (KitPreset preset : values()) {
            if (preset.id.equalsIgnoreCase(value) || preset.name().equalsIgnoreCase(value)) {
                return preset;
            }
        }
        return STANDARD;
    }

    /** The kit as it ships, before the player rearranges it. */
    public KitLayout defaultLayout() {
        KitLayout layout = new KitLayout();
        switch (this) {
            case STANDARD -> {
                hotbar(layout, SWORD, OBSIDIAN, END_CRYSTAL, RESPAWN_ANCHOR, GLOWSTONE, ENDER_PEARL, GOLDEN_APPLE,
                        EXPERIENCE_BOTTLE, TOTEM);
                row(layout, 9, END_CRYSTAL, OBSIDIAN, ENDER_PEARL, EXPERIENCE_BOTTLE, PICKAXE);
                layout.put(KitLayout.OFFHAND, TOTEM);
                return layout.withTotems(10);
            }
            case MCTIERS -> {
                hotbar(layout, SWORD, OBSIDIAN, END_CRYSTAL, RESPAWN_ANCHOR, GLOWSTONE, ENDER_PEARL, GOLDEN_APPLE,
                        EXPERIENCE_BOTTLE, TOTEM);
                row(layout, 9, END_CRYSTAL, END_CRYSTAL, END_CRYSTAL, OBSIDIAN, OBSIDIAN, RESPAWN_ANCHOR, GLOWSTONE,
                        ENDER_PEARL, EXPERIENCE_BOTTLE);
                row(layout, 18, PICKAXE, GOLDEN_APPLE);
                layout.put(KitLayout.OFFHAND, TOTEM);
                return layout.withTotems(KitLayout.FULL);
            }
            case MCPVP -> {
                hotbar(layout, SWORD, END_CRYSTAL, OBSIDIAN, RESPAWN_ANCHOR, GLOWSTONE, GOLDEN_APPLE, ENDER_PEARL,
                        EXPERIENCE_BOTTLE, TOTEM);
                row(layout, 9, END_CRYSTAL, END_CRYSTAL, OBSIDIAN, RESPAWN_ANCHOR, GLOWSTONE, GOLDEN_APPLE, ENDER_PEARL,
                        EXPERIENCE_BOTTLE, PICKAXE);
                layout.put(KitLayout.OFFHAND, TOTEM);
                return layout.withTotems(12);
            }
            case PVPHQ -> {
                hotbar(layout, SWORD, RESPAWN_ANCHOR, GLOWSTONE, OBSIDIAN, END_CRYSTAL, ENDER_PEARL, GOLDEN_APPLE,
                        EXPERIENCE_BOTTLE, TOTEM);
                row(layout, 9, RESPAWN_ANCHOR, RESPAWN_ANCHOR, GLOWSTONE, GLOWSTONE, END_CRYSTAL, OBSIDIAN,
                        EXPERIENCE_BOTTLE, PICKAXE);
                layout.put(KitLayout.OFFHAND, TOTEM);
                return layout.withTotems(14);
            }
            case LIGHT -> {
                hotbar(layout, SWORD, OBSIDIAN, END_CRYSTAL, RESPAWN_ANCHOR, GLOWSTONE, ENDER_PEARL, GOLDEN_APPLE,
                        EXPERIENCE_BOTTLE, TOTEM);
                row(layout, 9, END_CRYSTAL, OBSIDIAN, PICKAXE);
                layout.put(KitLayout.OFFHAND, TOTEM);
                return layout.withTotems(3);
            }
        }
        return layout;
    }

    private static void hotbar(KitLayout layout, KitItem... items) {
        row(layout, 0, items);
    }

    private static void row(KitLayout layout, int first, KitItem... items) {
        for (int index = 0; index < items.length; index++) {
            layout.put(first + index, items[index]);
        }
    }
}
