package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Items and slots for the practice screens, drawn straight from their texture files.
 *
 * <p>Item stacks cannot be built on the title screen in 26.x, where item components are only bound
 * once a world loads, so these screens never make one: a sweep of light stands in for the
 * enchantment glint, and stack sizes are drawn the way the inventory draws them.</p>
 */
final class PracticeIcons {
    static final int SLOT = 18;

    private PracticeIcons() {
    }

    /** A 16x16 texture from {@code textures/}, with an optional glint sweep. */
    static void texture(GuiGraphicsExtractor graphics, String texture, int x, int y, boolean glint, double seconds) {
        Identifier location = Identifier.withDefaultNamespace("textures/" + texture + ".png");
        graphics.blit(location, x, y, x + 16, y + 16, 0.0F, 1.0F, 0.0F, 1.0F);
        if (glint) {
            int sweep = (int) Math.floor((seconds * 14.0D) % 40.0D) - 12;
            for (int row = 0; row < 16; row++) {
                int left = x + sweep + row / 2;
                int right = Math.min(x + 16, left + 3);
                if (left >= x && left < right) {
                    graphics.fill(left, y + row, right, y + row + 1, 0x55C88CFF);
                }
            }
        }
    }

    /** A kit entry as the inventory shows it: the item, and its count when more than one. */
    static void entry(GuiGraphicsExtractor graphics, Font font, KitLayout.Entry entry, PracticeSettings.Armor armor, int x,
            int y, double seconds) {
        if (entry == null) {
            return;
        }
        texture(graphics, entry.item().texture(armor), x, y, entry.item().enchanted(), seconds);
        if (entry.count() > 1) {
            String count = Integer.toString(entry.count());
            graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
        }
    }

    /** The slot frame, darker inside like Vanilla's, lit while hovered or picked. */
    static void slot(GuiGraphicsExtractor graphics, int x, int y, boolean hovered, boolean accent) {
        int border = accent ? CrystalTheme.ACCENT : hovered ? CrystalTheme.CARD_BORDER_HOVER : CrystalTheme.CARD_BORDER;
        graphics.fill(x, y, x + SLOT, y + SLOT, border);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hovered ? 0xC0311544 : 0xC0160A20);
    }

    /** The label of an item for tooltips, with what it is enchanted with. */
    static String describe(KitLayout.Entry entry, PracticeSettings settings, boolean spanish) {
        KitItem item = entry.item();
        String name = item == KitItem.SWORD || item == KitItem.PICKAXE
                ? item.label(spanish) + " (" + settings.armor.label(spanish).toLowerCase(java.util.Locale.ROOT) + ")"
                : item.label(spanish);
        String enchants = switch (item) {
            case SWORD -> spanish
                    ? "Filo V · Empuje " + PracticeSettings.roman(settings.knockback) + " · Irrompibilidad III · Reparación"
                    : "Sharpness V · Knockback " + PracticeSettings.roman(settings.knockback) + " · Unbreaking III · Mending";
            case PICKAXE -> spanish ? "Eficiencia V · Irrompibilidad III · Reparación" : "Efficiency V · Unbreaking III · Mending";
            default -> "";
        };
        String count = entry.count() > 1 ? " ×" + entry.count() : "";
        return enchants.isEmpty() ? name + count : name + count + "\n" + enchants;
    }
}
