package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * A window over the options column, beside the live crystal, for a choice too long for a card (the
 * flash's shapes) or a colour (picker, hex and quick colours): what is chosen shows on the crystal at
 * once. Its rows scroll when they do not fit, and it closes with its button, Esc or a click beside
 * it. While it is up the screen hands it every click and key, and nothing under it answers.
 */
public final class HubWindow {
    private static final long OPEN_NANOS = 170_000_000L;
    private static final long CLOSE_NANOS = 120_000_000L;
    private static final int HEADER = 20;

    private final Rect area;
    private final String title;
    private final HubButton.Icon icon;
    private final RowList list;
    private final HubButton done;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final long openedAt = System.nanoTime();
    private long closedAt;

    public HubWindow(Rect area, String title, HubButton.Icon icon, RowList list, Component doneLabel, int control, Runnable onDone) {
        this.area = area;
        this.title = title;
        this.icon = icon;
        this.list = list;
        int pad = pad(area);
        int doneWidth = Math.min(90, Math.max(40, area.width() / 3));
        this.done = new HubButton(area.right() - pad - doneWidth, area.bottom() - pad - control, doneWidth, control, doneLabel,
                ignored -> onDone.run()).style(HubButton.Style.PRIMARY);
        list.setViewport(viewport(area, control));
        list.reveal(this.openedAt, 1);
        this.widgets.addAll(list.widgets());
        this.widgets.add(this.done);
    }

    /** From the window's edge to what is in it: four pixels inside its inner rule. */
    private static int pad(Rect area) {
        return GrimoireDraw.inset(area) + 4;
    }

    /** How wide the rows of a window over {@code area} are; its scrollbar sits right of them. */
    public static int listWidth(Rect area) {
        return Math.max(20, area.width() - pad(area) * 2 - 5);
    }

    /** Where the rows of a window over {@code area} show, between its title and its button. */
    public static Rect viewport(Rect area, int control) {
        int top = area.y() + pad(area) + HEADER;
        int bottom = area.bottom() - pad(area) - control - 4;
        return new Rect(area.x() + pad(area), top, listWidth(area), Math.max(10, bottom - top));
    }

    public List<AbstractWidget> widgets() {
        return this.widgets;
    }

    public RowList list() {
        return this.list;
    }

    public boolean contains(double mouseX, double mouseY) {
        return this.area.contains(mouseX, mouseY);
    }

    public void close(long now) {
        if (this.closedAt == 0L) {
            this.closedAt = now;
        }
    }

    public boolean closing() {
        return this.closedAt != 0L;
    }

    public boolean gone(long now) {
        return this.closedAt != 0L && now - this.closedAt >= CLOSE_NANOS;
    }

    private float shown(long now) {
        float in = HubMotion.easeOutCubic(HubMotion.progress(now - this.openedAt, OPEN_NANOS));
        if (this.closedAt == 0L) {
            return in;
        }
        return Math.min(in, 1.0F - HubMotion.easeInCubic(HubMotion.progress(now - this.closedAt, CLOSE_NANOS)));
    }

    /** Puts the rows where they are drawn this frame, as the screen does for its own list. */
    public void place(long now, float frameMillis) {
        float shown = shown(now);
        this.list.place(now, frameMillis, shown, !closing());
        this.done.visible = shown > 0.05F;
        this.done.active = !closing();
        this.done.setAlpha(shown);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, long now, int mouseX, int mouseY, float partialTick, HubSkin skin) {
        float shown = shown(now);
        if (shown <= 0.01F) {
            return;
        }
        Rect box = this.area;
        int pad = pad(box);
        int ruleY = box.y() + pad + HEADER - 4;
        HubDraw.isolate(graphics);
        if (GrimoireDraw.active) {
            // A loose leaf laid over the page, with its title in gothic letters.
            GrimoireDraw.page(graphics, box, GrimoireDraw.PAGE_DEEP, shown);
            // The title centred by its ink between the leaf's inner rule and its own.
            int bandY = box.y() + GrimoireDraw.inset(box) + 1;
            int band = ruleY - bandY;
            float scale = GrimoireDraw.gothicFit(band, 1.0F);
            GrimoireDraw.gothic(graphics, font, this.title, box.x() + pad, GrimoireDraw.gothicY(bandY, band, scale), scale,
                    CrystalTheme.fade(GrimoireDraw.TITLE, shown), 0.0F);
            graphics.fill(box.x() + pad, ruleY, box.right() - pad, ruleY + 1, CrystalTheme.fade(GrimoireDraw.RULE_TITLE, shown));
            HubDraw.isolate(graphics);
            this.list.render(graphics, font, now, shown, GrimoireDraw.ACCENT, GrimoireDraw.MUTED, GrimoireDraw.BORDER, mouseX, mouseY,
                    partialTick);
            if (this.done.visible) {
                this.done.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        HubDraw.glass(graphics, box.x(), box.y(), box.width(), box.height(), 0xF61C0C2E, 0xF60A0414,
                CrystalTheme.lerp(skin.accent, skin.accentBright, 0.3F), shown);
        // Light runs along the top edge as it opens.
        float sweep = HubMotion.easeOutCubic(HubMotion.progress(now - this.openedAt, 320_000_000L));
        int sweepWidth = Math.round((box.width() - 8) * sweep);
        graphics.fill(box.x() + 4, box.y() + 1, box.x() + 4 + sweepWidth, box.y() + 2,
                CrystalTheme.fade(CrystalTheme.withAlpha(skin.accentBright, Math.round(200 * (1.0F - sweep * 0.6F))), shown));
        int titleY = box.y() + pad + 2;
        int textX = box.x() + pad + 1;
        if (this.icon != null) {
            this.icon.draw(graphics, textX, titleY - 2, 12, CrystalTheme.fade(0xFFFFFFFF, shown));
            textX += 16;
        }
        CrystalUi.label(graphics, font, HubDraw.fit(font, this.title, Math.max(1, box.right() - pad - textX)), textX, titleY,
                CrystalTheme.fade(skin.title, shown), true);
        graphics.fill(box.x() + pad, ruleY, box.right() - pad, ruleY + 1, CrystalTheme.fade(CrystalTheme.withAlpha(skin.accent, 110), shown));
        HubDraw.isolate(graphics);
        this.list.render(graphics, font, now, shown, skin.accent, skin.muted, skin.border, mouseX, mouseY, partialTick);
        if (this.done.visible) {
            this.done.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, int step) {
        return this.list.mouseScrolled(mouseX, mouseY, amount, step);
    }
}
