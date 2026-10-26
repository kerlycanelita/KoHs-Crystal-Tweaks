package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A button drawn by an overlay itself (a notice) rather than a widget: an overlay is drawn over every
 * widget of the hub, so its own buttons have to be drawn after them and answer clicks before them.
 * The hitbox is the rectangle it is drawn in. It is a plate of the book, its word in gothic letters,
 * as every button there is.
 */
final class OverlayButton {
    private Rect rect = Rect.EMPTY;
    private final String label;
    private final boolean primary;
    private float hover;
    private long pressedAt = Long.MIN_VALUE / 2;

    OverlayButton(String label, boolean primary) {
        this.label = label;
        this.primary = primary;
    }

    void setRect(Rect rect) {
        this.rect = rect;
    }

    Rect rect() {
        return this.rect;
    }

    boolean contains(double mouseX, double mouseY) {
        return this.rect.contains(mouseX, mouseY);
    }

    void press() {
        this.pressedAt = System.nanoTime();
    }

    void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY, float alpha,
            boolean enabled) {
        if (alpha <= 0.02F || this.rect.isEmpty()) {
            return;
        }
        this.hover = HubMotion.damp(this.hover, enabled && contains(mouseX, mouseY) ? 1.0F : 0.0F, frameMillis, 55.0F);
        int fill = this.primary ? CrystalTheme.lerp(GrimoireDraw.ACCENT_FILL, 0xFF3A2160, this.hover)
                : CrystalTheme.lerp(0xFF140E1B, 0xFF1F1629, this.hover);
        int border = this.primary ? CrystalTheme.lerp(GrimoireDraw.ACCENT, 0xFFC084FC, this.hover)
                : CrystalTheme.lerp(GrimoireDraw.BORDER, 0xFF7A48C0, this.hover);
        if (!enabled) {
            border = GrimoireDraw.BORDER;
        }
        GrimoireDraw.plate(graphics, this.rect.x(), this.rect.y(), this.rect.width(), this.rect.height(), fill, border, alpha);
        float press = 1.0F - HubMotion.progress(now - this.pressedAt, 220_000_000L);
        if (press > 0.0F) {
            graphics.fill(this.rect.x() + 1, this.rect.y() + 1, this.rect.right() - 1, this.rect.bottom() - 1,
                    CrystalTheme.withAlpha(0xC084FC, Math.round(90 * press * press * alpha)));
        }
        int color = enabled ? CrystalTheme.lerp(this.primary ? GrimoireDraw.TITLE : GrimoireDraw.NAV, 0xFFFFFFFF, this.hover) : GrimoireDraw.FAINT;
        float scale = Math.min(GrimoireDraw.gothicFit(this.rect.height(), 1.0F),
                (this.rect.width() - 10) / (float) Math.max(1, GrimoireDraw.gothicWidth(font, this.label, 1.0F)));
        GrimoireDraw.gothicCentered(graphics, font, this.label, this.rect.centerX(), GrimoireDraw.gothicY(this.rect.y(), this.rect.height(), scale),
                scale, CrystalTheme.fade(color, alpha), 0.0F);
    }
}
