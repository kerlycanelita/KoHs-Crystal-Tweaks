package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A button drawn by an overlay itself (the intro, the optimizer window) rather than a widget: an
 * overlay is drawn over every widget of the hub, so its own buttons have to be drawn after them and
 * answer clicks before them. The hitbox is the rectangle it is drawn in.
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

    int preferredWidth(Font font) {
        return font.width(this.label) + 22;
    }

    void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY, float alpha,
            boolean enabled) {
        if (alpha <= 0.02F || this.rect.isEmpty()) {
            return;
        }
        HubSkin skin = HubSkin.current();
        this.hover = HubMotion.damp(this.hover, enabled && contains(mouseX, mouseY) ? 1.0F : 0.0F, frameMillis, 55.0F);
        int base = this.primary ? HubSkin.tint(skin.controlHover, skin.accent, 0.25F) : skin.controlFill;
        int top = CrystalTheme.lerp(base, HubSkin.tint(skin.controlHover, skin.accentBright, 0.2F), this.hover);
        if (!enabled) {
            top = 0xA0261A2C;
        }
        if (this.hover > 0.05F) {
            HubDraw.halo(graphics, this.rect.x(), this.rect.y(), this.rect.width(), this.rect.height(), skin.accent, 3,
                    0.5F * this.hover * alpha);
        }
        int border = this.primary
                ? CrystalTheme.lerp(skin.accent, skin.accentBright, HubMotion.breathe(now / 1_000_000_000.0D, 1.6D) * 0.6F)
                : CrystalTheme.lerp(skin.borderSoft | 0xFF000000, skin.accent, this.hover);
        HubDraw.glass(graphics, this.rect.x(), this.rect.y(), this.rect.width(), this.rect.height(), top, HubSkin.darken(top, 0.5F),
                enabled ? border : 0xA0584A60, alpha);
        float press = 1.0F - HubMotion.progress(now - this.pressedAt, 220_000_000L);
        if (press > 0.0F) {
            graphics.fill(this.rect.x() + 1, this.rect.y() + 1, this.rect.right() - 1, this.rect.bottom() - 1,
                    CrystalTheme.withAlpha(skin.accentBright, Math.round(120 * press * press * alpha)));
        }
        String text = HubDraw.fit(font, this.label, this.rect.width() - 8);
        CrystalUi.centered(graphics, font, text, this.rect.centerX(), this.rect.y() + (this.rect.height() - 8) / 2,
                CrystalTheme.fade(enabled ? CrystalTheme.lerp(skin.text, 0xFFFFFFFF, this.hover) : skin.disabled, alpha));
    }
}
