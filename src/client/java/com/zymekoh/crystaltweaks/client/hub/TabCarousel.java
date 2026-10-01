package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The tabs above the crystal, as a carousel: the selected tab sits in the middle and the others
 * line up on either side, smaller and dimmer, running off the edges when there are more than fit.
 * The mouse wheel over it turns it one tab at a time; a click picks a tab, and a click on the open
 * tab folds its drawer away again so the crystal gets the whole stage.
 *
 * <p>Few players guess that a row of tabs scrolls sideways, so until they have turned it once a
 * small mouse with a rolling wheel sits under it, saying so.</p>
 */
public final class TabCarousel {
    /** One tab: an id for the screen, its label and its animated icon. */
    public record Item(String id, String label, HubButton.Icon icon) {
    }

    private final List<Item> items;
    private Rect rect = Rect.EMPTY;
    private int selected;
    private float position;
    private boolean open;
    private boolean turnedByPlayer;
    private float[] hover;

    public TabCarousel(List<Item> items, int selected, boolean open) {
        this.items = items;
        this.selected = Math.max(0, Math.min(items.size() - 1, selected));
        this.position = this.selected;
        this.open = open;
        this.hover = new float[items.size()];
    }

    public void setRect(Rect rect) {
        this.rect = rect;
    }

    public Rect rect() {
        return this.rect;
    }

    public int selected() {
        return this.selected;
    }

    public Item selectedItem() {
        return this.items.get(this.selected);
    }

    /** Whether the selected tab's drawer is open. */
    public boolean open() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean turnedByPlayer() {
        return this.turnedByPlayer;
    }

    public void markTurned() {
        this.turnedByPlayer = true;
    }

    private int spacing(Font font) {
        int widest = 0;
        for (Item item : this.items) {
            widest = Math.max(widest, font.width(item.label()));
        }
        return Math.min(this.rect.width() / 2, widest + 34);
    }

    /** One step of the wheel: the next tab or the previous one, opening its drawer. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!this.rect.contains(mouseX, mouseY) || amount == 0.0D || this.items.size() < 2) {
            return false;
        }
        int next = Math.max(0, Math.min(this.items.size() - 1, this.selected - (int) Math.signum(amount)));
        this.turnedByPlayer = true;
        if (next != this.selected) {
            this.selected = next;
            this.open = true;
            return true;
        }
        return true;
    }

    /**
     * @return the id of the tab that changed, or {@code null} when the click missed every tab
     */
    public String mouseClicked(Font font, double mouseX, double mouseY) {
        if (!this.rect.contains(mouseX, mouseY)) {
            return null;
        }
        int spacing = spacing(font);
        for (int index = 0; index < this.items.size(); index++) {
            Rect box = itemBox(font, index, spacing);
            if (box.contains(mouseX, mouseY)) {
                if (index == this.selected) {
                    this.open = !this.open;
                } else {
                    this.selected = index;
                    this.open = true;
                    this.turnedByPlayer = true;
                }
                return this.items.get(index).id();
            }
        }
        // The chevrons at the ends step one tab.
        int step = mouseX < this.rect.centerX() ? -1 : 1;
        int next = Math.max(0, Math.min(this.items.size() - 1, this.selected + step));
        if (next != this.selected) {
            this.selected = next;
            this.open = true;
            this.turnedByPlayer = true;
            return this.items.get(next).id();
        }
        return null;
    }

    private Rect itemBox(Font font, int index, int spacing) {
        Item item = this.items.get(index);
        float distance = Math.abs(index - this.position);
        float scale = 1.0F - 0.16F * Math.min(1.0F, distance);
        int width = Math.round((font.width(item.label()) + 26) * scale);
        int height = Math.round((this.rect.height() - 2) * (1.0F - 0.12F * Math.min(1.0F, distance)));
        int centerX = Math.round(this.rect.centerX() + (index - this.position) * spacing);
        return new Rect(centerX - width / 2, this.rect.centerY() - height / 2, width, height);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY,
            float alpha, boolean showHint) {
        if (alpha <= 0.02F || this.rect.isEmpty()) {
            return;
        }
        HubSkin skin = HubSkin.current();
        this.position = HubMotion.damp(this.position, this.selected, frameMillis, 70.0F);
        if (Math.abs(this.position - this.selected) < 0.002F) {
            this.position = this.selected;
        }
        int spacing = spacing(font);
        // The rail: a thin glass bar the tabs ride on, fading out at both ends.
        int railY = this.rect.centerY();
        graphics.fillGradient(this.rect.x(), railY - 6, this.rect.right(), railY + 7,
                CrystalTheme.fade(0x50140820, alpha), CrystalTheme.fade(0x30140820, alpha));
        graphics.fill(this.rect.x() + 6, railY + 7, this.rect.right() - 6, railY + 8,
                CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 90), alpha));
        graphics.enableScissor(this.rect.x(), this.rect.y() - 2, this.rect.right(), this.rect.bottom() + 2);
        for (int index = 0; index < this.items.size(); index++) {
            Rect box = itemBox(font, index, spacing);
            if (box.right() < this.rect.x() || box.x() > this.rect.right()) {
                continue;
            }
            Item item = this.items.get(index);
            float distance = Math.min(1.0F, Math.abs(index - this.position));
            boolean hovered = box.contains(mouseX, mouseY) && this.rect.contains(mouseX, mouseY);
            this.hover[index] = HubMotion.damp(this.hover[index], hovered ? 1.0F : 0.0F, frameMillis, 55.0F);
            float itemAlpha = alpha * (1.0F - 0.5F * distance) * (0.85F + 0.15F * this.hover[index]);
            boolean current = index == this.selected;
            int top = current ? CrystalTheme.lerp(skin.controlHover, skin.accent, 0.18F) : skin.controlFill;
            HubDraw.glass(graphics, box.x(), box.y(), box.width(), box.height(), top, HubSkin.darken(top, 0.55F),
                    current ? CrystalTheme.lerp(skin.accent, skin.accentBright, HubMotion.breathe(now / 1e9D, 1.8D) * 0.5F)
                            : CrystalTheme.lerp(skin.borderSoft | 0xFF000000, skin.accent, this.hover[index]),
                    itemAlpha);
            int iconSize = Math.min(box.height() - 4, 11);
            int labelWidth = font.width(item.label());
            int groupWidth = iconSize + 4 + labelWidth;
            int x = box.centerX() - groupWidth / 2;
            int textColor = CrystalTheme.fade(current ? skin.title : skin.muted, itemAlpha);
            if (item.icon() != null) {
                item.icon().draw(graphics, x, box.centerY() - iconSize / 2, iconSize, textColor);
            }
            CrystalUi.label(graphics, font, item.label(), x + iconSize + 4, box.centerY() - 4, textColor);
            if (current) {
                // Open or folded, said by a small mark under the tab.
                int markY = box.bottom() - 2;
                int markWidth = this.open ? box.width() - 10 : 8;
                graphics.fill(box.centerX() - markWidth / 2, markY, box.centerX() + markWidth / 2, markY + 1,
                        CrystalTheme.fade(skin.accentBright, itemAlpha));
            }
        }
        graphics.disableScissor();
        // Chevrons where more tabs wait beyond the edge.
        int chevronColor = CrystalTheme.fade(CrystalTheme.withAlpha(skin.accentBright, 200), alpha);
        if (this.selected > 0) {
            HubDraw.chevron(graphics, this.rect.x() + 1, railY - 3, false, chevronColor);
        }
        if (this.selected < this.items.size() - 1) {
            HubDraw.chevron(graphics, this.rect.right() - 5, railY - 3, true, chevronColor);
        }
        if (showHint && !this.turnedByPlayer && this.items.size() > 1) {
            double seconds = now / 1_000_000_000.0D;
            float pulse = HubMotion.breathe(seconds, 2.0D);
            int hintX = this.rect.right() - 22;
            int hintY = this.rect.bottom() + 12;
            HubDraw.isolate(graphics);
            HubDraw.glass(graphics, hintX - 16, hintY - 10, 32, 21, 0xE01A0A2A, 0xE00B0414,
                    CrystalTheme.withAlpha(skin.accent, Math.round(140 + 90 * pulse)), alpha);
            HubDraw.wheelHint(graphics, hintX, hintY, seconds, alpha, true, skin.accent);
            HubDraw.isolate(graphics);
        }
    }
}
