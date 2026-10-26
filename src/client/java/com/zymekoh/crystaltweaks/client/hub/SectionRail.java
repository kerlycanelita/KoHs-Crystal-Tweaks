package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The rail of sections on a wing's outer edge: one live icon per section, the open one lit and tied
 * to its panel by a thread of light, the others dim until the pointer reaches them, when their name
 * slides out over the panel.
 */
public final class SectionRail {
    /** One section: its id, its name and its live icon. */
    public record Section(String id, String name, HubButton.Icon icon) {
    }

    private final List<Section> sections;
    /** On the left edge of the screen (the left wing): names slide out to the right. */
    private final boolean leftEdge;
    private final float[] hover;
    private int selected;

    /** The grimoire's list: each section a row with its icon and its name in gothic letters, rather than a strip of icons. */
    private boolean list;
    private int rowHeight = 18;

    public SectionRail(List<Section> sections, int selected, boolean leftEdge) {
        this.sections = sections;
        this.leftEdge = leftEdge;
        this.hover = new float[sections.size()];
        this.selected = Math.max(0, Math.min(sections.size() - 1, selected));
    }

    /** Shows the sections as a list of rows {@code rowHeight} tall, with their names. */
    public SectionRail asList(int rowHeight) {
        this.list = true;
        this.rowHeight = rowHeight;
        return this;
    }

    /** Every section's place, worked out once for the rail it was worked out for, not every frame. */
    private Rect[] places;
    private Rect placesFor;

    /** Where section {@code index} is: its row in a list, its button on a strip. */
    private Rect button(Rect rail, int index) {
        if (rail != this.placesFor || this.places == null) {
            int count = this.sections.size();
            this.places = new Rect[count];
            for (int at = 0; at < count; at++) {
                this.places[at] = this.list ? CrystalHubLayout.listRow(rail, at, count, this.rowHeight)
                        : CrystalHubLayout.railButton(rail, at, count);
            }
            this.placesFor = rail;
        }
        return this.places[index];
    }

    private int iconSize(Rect button) {
        return this.list ? Math.max(6, Math.min(14, button.height() - 4)) : Math.max(4, button.width() - 6);
    }

    public int size() {
        return this.sections.size();
    }

    public Section section(int index) {
        return this.sections.get(index);
    }

    public int selected() {
        return this.selected;
    }

    public Section selectedSection() {
        return this.sections.get(this.selected);
    }

    public int indexOf(String id) {
        for (int index = 0; index < this.sections.size(); index++) {
            if (this.sections.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    /** Opens another section. */
    public void select(int index) {
        this.selected = Math.max(0, Math.min(this.sections.size() - 1, index));
    }

    /** The section button under the pointer, or -1. */
    public int at(Rect rail, double mouseX, double mouseY) {
        for (int index = 0; index < this.sections.size(); index++) {
            if (button(rail, index).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    /** The list on the sections' page: icon, gothic name, and for the open one a lit name and a line of light to the page's edge. */
    private void renderList(GuiGraphicsExtractor graphics, Font font, Rect rail, float frame, int mouseX, int mouseY, float alpha,
            double seconds) {
        float response = 1.0F - (float) Math.exp(-frame / 70.0F);
        for (int index = 0; index < this.sections.size(); index++) {
            Rect row = button(rail, index);
            boolean open = index == this.selected;
            boolean hovered = row.contains(mouseX, mouseY);
            this.hover[index] += ((hovered ? 1.0F : 0.0F) - this.hover[index]) * response;
            float hover = this.hover[index];
            int iconSize = iconSize(row);
            int iconX = row.x() + 1;
            int iconY = row.y() + (row.height() - iconSize) / 2;
            if (open) {
                HubDraw.isolate(graphics);
                HubDraw.glowDisc(graphics, iconX + iconSize / 2, iconY + iconSize / 2, iconSize, GrimoireDraw.ACCENT,
                        (0.30F + 0.14F * HubMotion.breathe(seconds, 1.8D)) * alpha);
                HubDraw.isolate(graphics);
            }
            float iconAlpha = alpha * (open ? 1.0F : 0.78F + 0.22F * hover);
            this.sections.get(index).icon().draw(graphics, iconX, iconY, iconSize, CrystalTheme.fade(0xFFFFFFFF, iconAlpha));
            String name = this.sections.get(index).name();
            int textX = iconX + iconSize + 6 + Math.round(2.0F * hover * (open ? 0.0F : 1.0F));
            int room = row.right() - textX - (open ? 14 : 2);
            float scale = Math.min(1.0F, room / (float) Math.max(1, GrimoireDraw.gothicWidth(font, name, 1.0F)));
            if (scale < 0.72F) {
                // Too long for the gothic letters at a readable size: the plain ones, cut to fit.
                CrystalUi.label(graphics, font, HubDraw.fit(font, name, Math.max(1, room)), textX, row.y() + (row.height() - 8) / 2,
                        CrystalTheme.fade(open ? GrimoireDraw.TITLE : GrimoireDraw.NAV, alpha));
                continue;
            }
            int color = open ? 0xFFFFFFFF : CrystalTheme.lerp(GrimoireDraw.NAV, GrimoireDraw.TITLE, hover);
            float textY = row.y() + (row.height() - 9.0F * scale) / 2.0F;
            GrimoireDraw.gothic(graphics, font, name, textX, textY, scale, CrystalTheme.fade(color, alpha), open ? 1.0F : hover * 0.35F);
            if (open) {
                // The line of light from the name to the edge of the page.
                int lineX = textX + GrimoireDraw.gothicWidth(font, name, scale) + 4;
                int lineY = row.centerY();
                if (lineX < row.right() - 2) {
                    graphics.fill(lineX, lineY - 1, row.right(), lineY + 2,
                            CrystalTheme.withAlpha(GrimoireDraw.ACCENT, Math.round(50 * alpha)));
                    graphics.fill(lineX, lineY, row.right(), lineY + 1, CrystalTheme.fade(GrimoireDraw.ACCENT, alpha));
                }
            }
        }
    }

    /** @param panel the wing's panel, for the thread of light and the names that slide out over it */
    public void render(GuiGraphicsExtractor graphics, Font font, Rect rail, Rect panel, float frame, int mouseX, int mouseY, float alpha,
            HubSkin skin) {
        if (rail.isEmpty() || alpha <= 0.02F) {
            return;
        }
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        if (this.list) {
            renderList(graphics, font, rail, frame, mouseX, mouseY, alpha, seconds);
            return;
        }
        float response = 1.0F - (float) Math.exp(-frame / 70.0F);
        int count = this.sections.size();
        int named = -1;
        for (int index = 0; index < count; index++) {
            Rect button = button(rail, index);
            boolean open = index == this.selected;
            boolean hovered = button.contains(mouseX, mouseY);
            if (hovered && !open) {
                named = index;
            }
            this.hover[index] += ((hovered ? 1.0F : 0.0F) - this.hover[index]) * response;
            float lift = Math.max(this.hover[index], open ? 1.0F : 0.0F);
            if (open) {
                // The open section's thread of light into its panel, with a bead running along it.
                int threadY = button.centerY();
                int from = this.leftEdge ? button.right() : panel.right();
                int to = this.leftEdge ? panel.x() + 2 : button.x();
                int light = GrimoireDraw.active ? GrimoireDraw.ACCENT : skin.halo;
                graphics.fill(Math.min(from, to), threadY, Math.max(from, to), threadY + 1,
                        CrystalTheme.fade(CrystalTheme.withAlpha(light, 200), alpha));
                HubDraw.isolate(graphics);
                HubDraw.glowDisc(graphics, button.centerX(), button.centerY(), button.width(), light,
                        (0.28F + 0.12F * HubMotion.breathe(seconds, 1.8D)) * alpha);
                HubDraw.isolate(graphics);
            }
            if (GrimoireDraw.active) {
                GrimoireDraw.plate(graphics, button.x(), button.y(), button.width(), button.height(),
                        CrystalTheme.lerp(0xFF140E1B, GrimoireDraw.ACCENT_FILL, lift),
                        open ? GrimoireDraw.ACCENT : CrystalTheme.lerp(GrimoireDraw.BORDER, 0xFF7A48C0, this.hover[index]), alpha);
            } else {
                int top = CrystalTheme.lerp(0xB0140A22, skin.sideTop | 0xE0000000, lift);
                int bottom = CrystalTheme.lerp(0xB00A0512, skin.sideBottom | 0xE0000000, lift);
                HubDraw.glass(graphics, button.x(), button.y(), button.width(), button.height(), top, bottom,
                        open ? CrystalTheme.lerp(skin.sideBorder, skin.accentBright, 0.5F)
                                : CrystalTheme.lerp(0x80584A78, skin.sideBorder, this.hover[index]), alpha);
            }
            if (open) {
                // A blade of light on the rail's outer side marks the open section.
                int bladeX = this.leftEdge ? button.x() - 2 : button.right() + 1;
                graphics.fill(bladeX, button.y() + 2, bladeX + 1, button.bottom() - 2, CrystalTheme.fade(skin.accentBright, alpha));
            }
            int iconSize = Math.max(4, button.width() - 6);
            float iconAlpha = alpha * (open ? 1.0F : 0.55F + 0.45F * this.hover[index]);
            this.sections.get(index).icon().draw(graphics, button.x() + (button.width() - iconSize) / 2,
                    button.y() + (button.height() - iconSize) / 2, iconSize, CrystalTheme.fade(0xFFFFFFFF, iconAlpha));
        }
        if (named >= 0) {
            drawName(graphics, font, rail, panel, named, alpha, skin);
        }
    }

    /** The name of the section under the pointer, sliding out of its button over the panel. */
    private void drawName(GuiGraphicsExtractor graphics, Font font, Rect rail, Rect panel, int index, float alpha, HubSkin skin) {
        Rect button = button(rail, index);
        String name = this.sections.get(index).name();
        float out = HubMotion.easeOutCubic(this.hover[index]);
        int width = Math.min(panel.width() - 4, font.width(name) + 10);
        int height = 12;
        int travel = Math.round(6 * (1.0F - out));
        int x = this.leftEdge ? panel.x() + 2 - travel : panel.right() - 2 - width + travel;
        int y = button.centerY() - height / 2;
        HubDraw.isolate(graphics);
        HubDraw.glass(graphics, x, y, width, height, 0xF0241038, 0xF0120820, skin.accentBright, alpha * out);
        CrystalUi.label(graphics, font, HubDraw.fit(font, name, width - 8), x + 5, y + 2, CrystalTheme.fade(0xFFFFFFFF, alpha * out));
        HubDraw.isolate(graphics);
    }
}
