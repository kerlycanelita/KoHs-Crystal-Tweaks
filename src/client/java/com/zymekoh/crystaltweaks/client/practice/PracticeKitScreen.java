package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import com.zymekoh.crystaltweaks.practice.KitPreset;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The practice kit editor: an inventory to arrange the chosen kit in, the way the kit editors of
 * PvP servers work.
 *
 * <p>Click an item to pick it up and click a slot to put it down, swapping with what was there;
 * dragging works too. Right click empties a slot. The kit room above hands out any item of the kit,
 * which is how a stack goes back in after being removed. Totems can be moved like everything else;
 * how many there are is also set with the counter. Nothing is kept until "Save"; "Reset" brings back
 * the preset as it ships.</p>
 */
public final class PracticeKitScreen extends Screen {
    private static final int S = PracticeIcons.SLOT;
    private static final long NOTICE_NANOS = 1_800_000_000L;
    private static final KitItem[] ROOM = KitItem.values();

    private final Screen parent;
    private final boolean spanish;
    private final KitPreset preset;
    private final PracticeSettings settings;
    private KitLayout layout;
    private KitLayout saved;
    private KitLayout.Entry cursor;
    private int cursorOrigin = -1;
    private int pressedSlot = -1;
    private boolean confirmLeave;
    private long noticeAt;
    private String notice = "";
    private long openedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int gridX;
    private int gridY;
    private int hotbarY;
    private int roomX;
    private int roomY;
    private int armorX;
    private int offhandX;
    private PurpleCloseButton leaveButton;

    public PracticeKitScreen(Screen parent, KitPreset preset) {
        super(Component.literal("Crystal Practice Kit"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
        this.preset = preset;
        this.settings = CrystalVisualConfig.practice();
        this.layout = CrystalVisualConfig.practiceKit(preset).copy();
        this.saved = this.layout.copy();
    }

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        int margin = Mth.clamp(this.width / 24, 6, 20);
        int contentWidth = 9 * S + 2 * (S + 14);
        this.panelWidth = Math.min(this.width - margin * 2, Math.max(contentWidth + 28, 300));
        this.panelHeight = Math.min(this.height - margin * 2, 250);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.gridX = this.panelX + (this.panelWidth - 9 * S) / 2;
        this.armorX = this.gridX - S - 8;
        this.offhandX = this.gridX + 9 * S + 14;
        int roomWidth = ROOM.length * S;
        this.roomX = this.panelX + (this.panelWidth - roomWidth) / 2;
        this.roomY = this.panelY + 50;
        this.gridY = this.roomY + S + 20;
        this.hotbarY = this.gridY + 3 * S + 4;

        // The totem counter sits on the kit's own title row, at the grid's right edge.
        int counterY = this.gridY - 14;
        int counterX = this.gridX + 9 * S - 14 * 2 - 58;
        addRenderableWidget(new PurpleCloseButton(counterX, counterY, 14, 12, Component.literal("-"), ignored -> totems(false)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish ? "Menos tótems" : "Fewer totems")));
        addRenderableWidget(new PurpleCloseButton(counterX + 14 + 58, counterY, 14, 12, Component.literal("+"),
                ignored -> totems(true)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish ? "Más tótems" : "More totems")));

        int footerY = this.panelY + this.panelHeight - 24;
        int buttonWidth = Math.max(60, Math.min(96, (this.panelWidth - 32) / 3));
        int buttonsX = this.panelX + (this.panelWidth - buttonWidth * 3 - 12) / 2;
        addRenderableWidget(new PurpleCloseButton(buttonsX, footerY, buttonWidth, 18,
                Component.literal(this.spanish ? "Restablecer" : "Reset"), ignored -> reset()))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Vuelve al kit " + this.preset.label(true) + " tal como viene. Guarda para quedártelo."
                        : "Back to the " + this.preset.label(false) + " kit as it ships. Save to keep it.")));
        PurpleCloseButton save = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + 6, footerY, buttonWidth, 18,
                Component.literal(this.spanish ? "Guardar" : "Save"), ignored -> save()).icon(PurpleCloseButton.Icon.CHECK));
        save.setSelected(true);
        this.leaveButton = addRenderableWidget(new PurpleCloseButton(buttonsX + (buttonWidth + 6) * 2, footerY, buttonWidth, 18,
                Component.literal(leaveLabel()), ignored -> leave()));
    }

    private String leaveLabel() {
        if (this.confirmLeave) {
            return this.spanish ? "Salir sin guardar" : "Leave unsaved";
        }
        return this.spanish ? "Listo" : "Done";
    }

    private boolean dirty() {
        return !this.layout.equals(this.saved) || this.cursor != null;
    }

    private void totems(boolean more) {
        returnCursor();
        this.layout = this.layout.withTotems(KitLayout.nextTotemStep(this.layout, more));
        touch();
    }

    private void reset() {
        this.cursor = null;
        this.cursorOrigin = -1;
        this.layout = this.preset.defaultLayout();
        show(this.spanish ? "Kit restablecido (sin guardar)" : "Kit reset (not saved yet)");
        touch();
    }

    private void save() {
        returnCursor();
        if (this.layout.isEmpty()) {
            show(this.spanish ? "Un kit vacío no se guarda" : "An empty kit is not saved");
            return;
        }
        CrystalVisualConfig.setPracticeKit(this.preset, this.layout);
        CrystalVisualConfig.save();
        this.saved = this.layout.copy();
        show(this.spanish ? "Kit guardado" : "Kit saved");
        touch();
    }

    private void leave() {
        if (dirty() && !this.confirmLeave) {
            this.confirmLeave = true;
            this.leaveButton.setMessage(Component.literal(leaveLabel()));
            show(this.spanish ? "Hay cambios sin guardar" : "There are unsaved changes");
            return;
        }
        this.minecraft.setScreen(this.parent);
    }

    private void touch() {
        if (this.confirmLeave) {
            this.confirmLeave = false;
            if (this.leaveButton != null) {
                this.leaveButton.setMessage(Component.literal(leaveLabel()));
            }
        }
    }

    private void show(String text) {
        this.notice = text;
        this.noticeAt = System.nanoTime();
    }

    // ------------------------------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------------------------------

    /** The kit slot under the mouse, or -1. */
    private int slotAt(double mouseX, double mouseY) {
        if (mouseY >= this.gridY && mouseY < this.gridY + 3 * S && mouseX >= this.gridX && mouseX < this.gridX + 9 * S) {
            int column = (int) ((mouseX - this.gridX) / S);
            int row = (int) ((mouseY - this.gridY) / S);
            return KitLayout.HOTBAR + row * 9 + column;
        }
        if (mouseY >= this.hotbarY && mouseY < this.hotbarY + S) {
            if (mouseX >= this.gridX && mouseX < this.gridX + 9 * S) {
                return (int) ((mouseX - this.gridX) / S);
            }
            if (mouseX >= this.offhandX && mouseX < this.offhandX + S) {
                return KitLayout.OFFHAND;
            }
        }
        return -1;
    }

    private int roomAt(double mouseX, double mouseY) {
        if (mouseY >= this.roomY && mouseY < this.roomY + S && mouseX >= this.roomX && mouseX < this.roomX + ROOM.length * S) {
            return (int) ((mouseX - this.roomX) / S);
        }
        return -1;
    }

    private int slotX(int slot) {
        if (slot == KitLayout.OFFHAND) {
            return this.offhandX;
        }
        return this.gridX + (slot % 9) * S;
    }

    private int slotY(int slot) {
        if (slot == KitLayout.OFFHAND || slot < KitLayout.HOTBAR) {
            return this.hotbarY;
        }
        return this.gridY + ((slot - KitLayout.HOTBAR) / 9) * S;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int slot = slotAt(mouseX, mouseY);
        int room = roomAt(mouseX, mouseY);
        if (event.button() == 1) {
            if (slot >= 0 && this.cursor == null && this.layout.get(slot) != null) {
                this.layout.set(slot, null);
                touch();
                return true;
            }
            if (this.cursor != null) {
                // Right click anywhere drops what is held back where it came from.
                returnCursor();
                return true;
            }
            return false;
        }
        if (event.button() != 0) {
            return false;
        }
        if (room >= 0) {
            // The kit room: take a full stack, or put back what is held.
            if (this.cursor != null) {
                this.cursor = null;
                this.cursorOrigin = -1;
            } else {
                this.cursor = KitLayout.Entry.full(ROOM[room]);
                this.cursorOrigin = -1;
            }
            touch();
            return true;
        }
        if (slot < 0) {
            if (this.cursor != null && outsidePanel(mouseX, mouseY)) {
                // Dropped outside the window: the stack leaves the kit.
                this.cursor = null;
                this.cursorOrigin = -1;
                touch();
                return true;
            }
            return false;
        }
        if (this.cursor == null) {
            KitLayout.Entry picked = this.layout.get(slot);
            if (picked != null) {
                this.cursor = picked;
                this.cursorOrigin = slot;
                this.layout.set(slot, null);
                this.pressedSlot = slot;
                touch();
            }
            return true;
        }
        place(slot);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int pressed = this.pressedSlot;
        this.pressedSlot = -1;
        if (event.button() == 0 && this.cursor != null && pressed >= 0) {
            int slot = slotAt(event.x(), event.y());
            if (slot >= 0 && slot != pressed) {
                // A drag from one slot to another.
                place(slot);
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    /** Puts the held stack in a slot, taking what was there onto the cursor. */
    private void place(int slot) {
        KitLayout.Entry there = this.layout.get(slot);
        this.layout.set(slot, this.cursor);
        if (there != null && this.cursorOrigin >= 0 && this.layout.get(this.cursorOrigin) == null) {
            // A plain swap: what was there goes where the held stack came from.
            this.layout.set(this.cursorOrigin, there);
            there = null;
        }
        this.cursor = there;
        this.cursorOrigin = there != null ? slot : -1;
        touch();
    }

    /** Puts a held stack back where it was, or in the first free slot. */
    private void returnCursor() {
        if (this.cursor == null) {
            return;
        }
        int target = this.cursorOrigin >= 0 && this.layout.get(this.cursorOrigin) == null ? this.cursorOrigin : -1;
        for (int slot = 0; target < 0 && slot < KitLayout.INVENTORY; slot++) {
            if (this.layout.get(slot) == null) {
                target = slot;
            }
        }
        if (target >= 0) {
            this.layout.set(target, this.cursor);
        }
        this.cursor = null;
        this.cursorOrigin = -1;
    }

    private boolean outsidePanel(double mouseX, double mouseY) {
        return mouseX < this.panelX || mouseX > this.panelX + this.panelWidth || mouseY < this.panelY
                || mouseY > this.panelY + this.panelHeight;
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x9A05020A);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0D;
        float enter = CrystalTheme.easeOutCubic(Mth.clamp((now - this.openedAt) / 300_000_000.0F, 0.0F, 1.0F));
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        CrystalUi.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(0xEE1B0928, 0.4F + 0.6F * enter), CrystalTheme.fade(0xEE0A0310, 0.4F + 0.6F * enter));
        CrystalUi.roundedOutline(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(CrystalTheme.PANEL_BORDER, enter));
        CrystalUi.crystalIcon(graphics, this.panelX + 14, this.panelY + 10, 9, seconds, enter);
        String title = (this.spanish ? "Ordenar inventario · " : "Arrange inventory · ") + this.preset.label(this.spanish);
        CrystalUi.label(graphics, this.font, this.font.plainSubstrByWidth(title, this.panelWidth - 40), this.panelX + 24,
                this.panelY + 6, CrystalTheme.fade(CrystalTheme.TITLE, enter));
        graphics.fill(this.panelX + 6, this.panelY + 18, this.panelX + this.panelWidth - 6, this.panelY + 19,
                CrystalTheme.fade(CrystalTheme.HEADER_LINE, enter));

        // The line under the title carries the hint, or for a moment what just happened.
        String hint = this.spanish
                ? "Clic para tomar y soltar · clic derecho vacía"
                : "Click to pick up and drop · right click empties";
        int hintColor = CrystalTheme.TEXT_MUTED;
        long sinceNotice = now - this.noticeAt;
        if (sinceNotice < NOTICE_NANOS && !this.notice.isEmpty()) {
            hint = this.notice;
            hintColor = CrystalTheme.fade(CrystalTheme.ACCENT_BRIGHT,
                    1.0F - Mth.clamp((sinceNotice - NOTICE_NANOS * 0.6F) / (NOTICE_NANOS * 0.4F), 0.0F, 1.0F));
        } else if (dirty()) {
            hint = this.spanish ? "Sin guardar" : "Not saved";
            hintColor = CrystalTheme.STATUS_PAUSED;
        }
        CrystalUi.label(graphics, this.font, this.font.plainSubstrByWidth(hint, this.panelWidth - 20), this.panelX + 10,
                this.panelY + 25, CrystalTheme.fade(hintColor, enter));
        int totems = this.layout.totems();
        String counter = this.layout.full() ? (this.spanish ? "Lleno " : "Full ") + totems : totems + (this.spanish ? " tót." : " tot.");
        int counterX = this.gridX + 9 * S - 14 - 58;
        CrystalUi.centered(graphics, this.font, counter, counterX + 29, this.gridY - 12,
                CrystalTheme.fade(CrystalTheme.ACCENT_BRIGHT, enter));

        CrystalUi.label(graphics, this.font, this.spanish ? "Sala de kits" : "Kit room", this.roomX, this.roomY - 11,
                CrystalTheme.fade(CrystalTheme.LEGEND, enter));
        int hoveredRoom = roomAt(mouseX, mouseY);
        for (int index = 0; index < ROOM.length; index++) {
            int x = this.roomX + index * S;
            PracticeIcons.slot(graphics, x, this.roomY, index == hoveredRoom, false);
            PracticeIcons.texture(graphics, ROOM[index].texture(this.settings.armor), x + 1, this.roomY + 1,
                    ROOM[index].enchanted(), seconds);
        }

        CrystalUi.label(graphics, this.font, this.spanish ? "Tu kit" : "Your kit", this.gridX, this.gridY - 11,
                CrystalTheme.fade(CrystalTheme.LEGEND, enter));
        int hovered = slotAt(mouseX, mouseY);
        for (int slot = 0; slot < KitLayout.INVENTORY; slot++) {
            drawSlot(graphics, slot, hovered, seconds);
        }
        drawSlot(graphics, KitLayout.OFFHAND, hovered, seconds);
        CrystalUi.centered(graphics, this.font, this.spanish ? "Mano izq." : "Off hand", this.offhandX + S / 2,
                this.hotbarY + S + 3, CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter));
        for (int index = 1; index <= 9; index++) {
            CrystalUi.centered(graphics, this.font, Integer.toString(index), this.gridX + (index - 1) * S + S / 2,
                    this.hotbarY + S + 3, CrystalTheme.fade(CrystalTheme.TEXT_DISABLED, enter));
        }
        drawArmor(graphics, seconds);

        String[] summary = summary();
        for (int line = 0; line < summary.length; line++) {
            CrystalUi.centered(graphics, this.font, this.font.plainSubstrByWidth(summary[line], this.panelWidth - 20),
                    this.panelX + this.panelWidth / 2, this.hotbarY + S + 15 + line * 10,
                    CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (this.cursor != null) {
            PracticeIcons.entry(graphics, this.font, this.cursor, this.settings.armor, mouseX - 8, mouseY - 8, seconds);
        } else {
            drawTooltip(graphics, mouseX, mouseY, hovered, hoveredRoom);
        }
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int slot, int hovered, double seconds) {
        int x = slotX(slot);
        int y = slotY(slot);
        PracticeIcons.slot(graphics, x, y, slot == hovered, slot == this.cursorOrigin && this.cursor != null);
        PracticeIcons.entry(graphics, this.font, this.layout.get(slot), this.settings.armor, x + 1, y + 1, seconds);
    }

    /** The armour, worn and not movable, beside the inventory as the player's own screen has it. */
    private void drawArmor(GuiGraphicsExtractor graphics, double seconds) {
        String material = this.settings.armor.name().toLowerCase(Locale.ROOT);
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int index = 0; index < pieces.length; index++) {
            int y = this.gridY + index * S + (index == 3 ? 4 : 0);
            PracticeIcons.slot(graphics, this.armorX, y, false, false);
            PracticeIcons.texture(graphics, "item/" + material + "_" + pieces[index], this.armorX + 1, y + 1, true, seconds);
        }
    }

    private String[] summary() {
        int crystals = this.layout.count(KitItem.END_CRYSTAL);
        int obsidian = this.layout.count(KitItem.OBSIDIAN);
        int anchors = this.layout.count(KitItem.RESPAWN_ANCHOR);
        int glowstone = this.layout.count(KitItem.GLOWSTONE);
        int apples = this.layout.count(KitItem.GOLDEN_APPLE);
        int pearls = this.layout.count(KitItem.ENDER_PEARL);
        int bottles = this.layout.count(KitItem.EXPERIENCE_BOTTLE);
        return this.spanish
                ? new String[] {this.layout.totems() + " tótems · " + crystals + " cristales · " + obsidian + " obsidiana · "
                        + anchors + " anclas", glowstone + " piedra lum. · " + apples + " manzanas · " + pearls + " perlas · "
                        + bottles + " XP"}
                : new String[] {this.layout.totems() + " totems · " + crystals + " crystals · " + obsidian + " obsidian · "
                        + anchors + " anchors", glowstone + " glowstone · " + apples + " apples · " + pearls + " pearls · "
                        + bottles + " XP bottles"};
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int hovered, int hoveredRoom) {
        String text = null;
        if (hovered >= 0 && this.layout.get(hovered) != null) {
            text = PracticeIcons.describe(this.layout.get(hovered), this.settings, this.spanish);
        } else if (hoveredRoom >= 0) {
            text = PracticeIcons.describe(KitLayout.Entry.full(ROOM[hoveredRoom]), this.settings, this.spanish) + "\n"
                    + (this.spanish ? "Clic para tomar un stack" : "Click to take a stack");
        } else if (mouseX >= this.armorX && mouseX < this.armorX + S && mouseY >= this.gridY && mouseY < this.gridY + 4 * S + 4) {
            text = this.settings.describe(this.spanish);
        }
        if (text == null) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.addAll(CrystalUi.wrap(this.font, line, 200));
        }
        CrystalUi.tooltip(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    @Override
    public void onClose() {
        leave();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
