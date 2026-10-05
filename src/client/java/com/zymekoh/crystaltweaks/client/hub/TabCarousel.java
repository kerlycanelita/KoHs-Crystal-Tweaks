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
 * small mouse with a rolling wheel sits under it, saying so. Tabs near the ends fade out before
 * they reach the edge, so no tab or icon is ever seen cut in half; and the open tab carries a small
 * End Crystal that hops now and then, the sign that clicking it again gives the crystal back its
 * place in the middle.</p>
 */
public final class TabCarousel {
    private static final net.minecraft.resources.Identifier END_CRYSTAL = HubDraw.vanilla("textures/item/end_crystal.png");
    /** How far in from either end a tab is fully drawn; nearer the edge it fades away. */
    private static final float EDGE_FADE = 20.0F;

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

    /** An unselected tab: its icon alone, in a small pill. */
    private int compactWidth() {
        return iconSize() + 12;
    }

    private int iconSize() {
        return Math.max(6, Math.min(this.rect.height() - 6, 11));
    }

    /** The selected tab: icon, name and, while open, the fold sign. */
    private int fullWidth(Font font, int index) {
        int extra = index == this.selected && this.open ? 12 : 0;
        return iconSize() + 4 + font.width(this.items.get(index).label()) + 18 + extra;
    }

    /** From the middle of the selected tab to the middle of its neighbour. */
    private int spacing(Font font) {
        int widest = 0;
        for (int index = 0; index < this.items.size(); index++) {
            widest = Math.max(widest, fullWidth(font, index));
        }
        return Math.min(this.rect.width() / 2, widest / 2 + 5 + compactWidth() / 2);
    }

    /** Where a tab sits, {@code distance} tabs from the selected one: a wide step, then narrow ones. */
    private float offset(float distance, int spacing) {
        float steps = Math.abs(distance);
        float near = Math.min(1.0F, steps) * spacing;
        float far = Math.max(0.0F, steps - 1.0F) * (compactWidth() + 5);
        return Math.signum(distance) * (near + far);
    }

    /** The name of an unselected tab under the pointer, for its tooltip; {@code null} elsewhere. */
    public String hoveredLabel(Font font, double mouseX, double mouseY) {
        if (!this.rect.contains(mouseX, mouseY)) {
            return null;
        }
        int spacing = spacing(font);
        for (int index = 0; index < this.items.size(); index++) {
            if (index != this.selected && itemBox(font, index, spacing).contains(mouseX, mouseY)) {
                return this.items.get(index).label();
            }
        }
        return null;
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
            // A tab faded out at the edge is not there to be clicked.
            float inside = Math.min(box.x() - this.rect.x(), this.rect.right() - box.right());
            if (box.contains(mouseX, mouseY) && (inside + 4.0F) / EDGE_FADE > 0.3F) {
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

    /** The open tab, as drawn: where its fold sign and its tooltip go. Empty while folded. */
    public Rect openTabBox(Font font) {
        if (!this.open || this.rect.isEmpty()) {
            return Rect.EMPTY;
        }
        return itemBox(font, this.selected, spacing(font));
    }

    private Rect itemBox(Font font, int index, int spacing) {
        float distance = index - this.position;
        float near = 1.0F - Math.min(1.0F, Math.abs(distance));
        // Full width at the middle, the icon's pill one step away and beyond.
        int width = Math.round(compactWidth() + (fullWidth(font, index) - compactWidth()) * near);
        int height = Math.round((this.rect.height() - 2) * (0.88F + 0.12F * near));
        int centerX = Math.round(this.rect.centerX() + offset(distance, spacing));
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
            // Fully drawn well inside the rail, gone before it would touch an edge.
            float inside = Math.min(box.x() - this.rect.x(), this.rect.right() - box.right());
            float edge = HubMotion.smoothstep(0.0F, 1.0F, HubMotion.clamp01((inside + 4.0F) / EDGE_FADE));
            if (edge <= 0.02F) {
                continue;
            }
            Item item = this.items.get(index);
            float distance = Math.min(1.0F, Math.abs(index - this.position));
            boolean hovered = box.contains(mouseX, mouseY) && this.rect.contains(mouseX, mouseY);
            this.hover[index] = HubMotion.damp(this.hover[index], hovered ? 1.0F : 0.0F, frameMillis, 55.0F);
            float itemAlpha = alpha * edge * (1.0F - 0.5F * distance) * (0.85F + 0.15F * this.hover[index]);
            boolean current = index == this.selected;
            int top = current ? CrystalTheme.lerp(skin.controlHover, skin.accent, 0.18F) : skin.controlFill;
            HubDraw.glass(graphics, box.x(), box.y(), box.width(), box.height(), top, HubSkin.darken(top, 0.55F),
                    current ? CrystalTheme.lerp(skin.accent, skin.accentBright, HubMotion.breathe(now / 1e9D, 1.8D) * 0.5F)
                            : CrystalTheme.lerp(skin.borderSoft | 0xFF000000, skin.accent, this.hover[index]),
                    itemAlpha);
            int iconSize = Math.min(box.height() - 4, iconSize());
            int labelWidth = font.width(item.label());
            boolean folds = current && this.open;
            // The name shows on the tab at the middle only; the others are their icons.
            float named = HubMotion.clamp01(1.0F - Math.abs(index - this.position) * 1.6F);
            boolean labelled = named > 0.05F && box.width() >= iconSize + 4 + labelWidth + 10;
            int groupWidth = labelled ? iconSize + 4 + labelWidth + (folds ? 12 : 0) : iconSize;
            int x = box.centerX() - groupWidth / 2;
            int textColor = CrystalTheme.fade(current ? skin.title : skin.muted, itemAlpha);
            if (item.icon() != null) {
                item.icon().draw(graphics, x, box.centerY() - iconSize / 2, iconSize,
                        CrystalTheme.fade(current ? 0xFFFFFFFF : CrystalTheme.lerp(0xFFB8A8C8, 0xFFFFFFFF, this.hover[index]), itemAlpha));
            }
            if (labelled) {
                CrystalUi.label(graphics, font, item.label(), x + iconSize + 4, box.centerY() - 4, CrystalTheme.fade(textColor, named));
            }
            if (folds && labelled) {
                // A crystal that hops up every couple of seconds: click the tab, the crystal goes back up.
                double seconds = now / 1_000_000_000.0D;
                float hop = (float) Math.max(0.0D, Math.sin(seconds * 3.2D)) * (seconds % 2.4D < 1.0D ? 1.6F : 0.0F);
                int signSize = Math.max(6, iconSize - 2);
                int signX = x + iconSize + 4 + labelWidth + 4;
                HubDraw.icon(graphics, END_CRYSTAL, signX + signSize / 2.0F, box.centerY() - hop, signSize, 0.0F,
                        CrystalTheme.fade(CrystalTheme.lerp(0xC0FFFFFF, 0xFFFFFFFF, this.hover[index]), itemAlpha));
            }
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
