package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * The hub's button, in the colours of {@link HubSkin}: dark glass, a border that brightens on hover,
 * an optional icon, a selected state and an on/off switch whose knob slides. A button on a side
 * panel is "lit": it takes on the glow colour with its panel. Every animation runs inside the
 * widget's own bounds, so the hitbox is always what is drawn.
 */
public final class HubButton extends AbstractButton implements Clippable {
    /** Draws an icon in a square of {@code size} pixels at (x, y). */
    public interface Icon {
        void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color);
    }

    public enum Style { NORMAL, PRIMARY, DANGER, HERZIUM }

    private static final long PRESS_FLASH_NANOS = 240_000_000L;

    private final Consumer<HubButton> action;
    private float hover;
    private float select;
    private float knob = -1.0F;
    private long lastFrame = System.nanoTime();
    private long pressedAt;
    private boolean pressed;
    private Icon icon;
    private BooleanSupplier switchState;
    private boolean selected;
    private boolean lit;
    private Style style = Style.NORMAL;

    public HubButton(int x, int y, int width, int height, Component message, Consumer<HubButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    public HubButton icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public HubButton style(Style style) {
        this.style = style == null ? Style.NORMAL : style;
        return this;
    }

    /** Lit by the glow, like the side panel it sits on. */
    public HubButton lit() {
        this.lit = true;
        return this;
    }

    public HubButton switchOf(BooleanSupplier state) {
        this.switchState = state;
        return this;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.pressedAt = System.nanoTime();
        this.pressed = true;
        this.action.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        long now = System.nanoTime();
        float frame = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        this.hover = HubMotion.damp(this.hover, isHoveredOrFocused() && this.active ? 1.0F : 0.0F, frame, 55.0F);
        this.select = HubMotion.damp(this.select, this.selected ? 1.0F : 0.0F, frame, 70.0F);
        float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        float lift = Math.max(this.hover, this.select * 0.85F);

        int accent = this.lit ? skin.litAccent() : skin.accent;
        int fillTop;
        int fillBottom;
        if (this.style == Style.DANGER) {
            fillTop = CrystalTheme.lerp(0xC0601420, 0xE08A1E30, lift);
            fillBottom = CrystalTheme.lerp(0xC02A0810, 0xE0400A16, lift);
            accent = 0xFFFF5A6E;
        } else if (this.style == Style.PRIMARY) {
            fillTop = CrystalTheme.lerp(HubSkin.tint(skin.controlFill, accent, 0.30F), HubSkin.tint(skin.controlHover, accent, 0.4F), lift);
            fillBottom = HubSkin.darken(fillTop, 0.55F);
        } else {
            fillTop = this.lit ? skin.litControlFill(lift) : CrystalTheme.lerp(skin.controlFill, skin.controlHover, lift);
            fillBottom = HubSkin.darken(fillTop, 0.6F);
        }
        if (!this.active && !this.selected) {
            fillTop = 0xA0261A2C;
            fillBottom = 0xA0140C18;
        }
        graphics.fillGradient(x + 1, y, x + width - 1, y + height, CrystalTheme.fade(fillTop, fade), CrystalTheme.fade(fillBottom, fade));
        graphics.fill(x, y + 1, x + 1, y + height - 1, CrystalTheme.fade(fillTop, fade * 0.8F));
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, CrystalTheme.fade(fillTop, fade * 0.8F));

        int border;
        if (this.selected) {
            float shine = HubMotion.breathe(now / 1_000_000_000.0D, 1.6D);
            border = CrystalTheme.lerp(accent, skin.accentBright, 0.35F + 0.4F * shine);
        } else if (!this.active) {
            border = 0x80584A60;
        } else {
            border = CrystalTheme.lerp(HubSkin.tint(skin.borderSoft | 0xFF000000, accent, 0.5F), accent, this.hover);
        }
        CrystalUi.outline(graphics, x, y, width, height, CrystalTheme.fade(border, fade * (this.active ? 0.95F : 0.6F)));
        if (this.selected || this.hover > 0.05F) {
            // A short bar of light along the bottom edge, the selected/hovered mark.
            int barWidth = Math.round((width - 6) * Math.max(this.select, this.hover));
            int barX = x + (width - barWidth) / 2;
            graphics.fill(barX, y + height - 2, barX + barWidth, y + height - 1,
                    CrystalTheme.fade(CrystalTheme.withAlpha(skin.accentBright, 200), fade));
        }
        if (this.style == Style.HERZIUM && this.active) {
            CrystalUi.herziumWave(graphics, x + 2, y + 1, width - 4, height - 2, now, fade);
        }
        long sincePress = now - this.pressedAt;
        if (this.pressed && sincePress >= 0L && sincePress < PRESS_FLASH_NANOS) {
            float t = sincePress / (float) PRESS_FLASH_NANOS;
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    CrystalTheme.withAlpha(skin.accentBright, Math.round(110.0F * (1.0F - t) * (1.0F - t) * fade)));
        }

        Font font = Minecraft.getInstance().font;
        int textColor = CrystalTheme.fade(this.active || this.selected
                ? CrystalTheme.lerp(skin.text, 0xFFFFFFFF, lift) : skin.disabled, fade);
        int textY = y + (height - 8) / 2;
        String label = getMessage().getString();
        int iconSize = this.icon == null ? 0 : Math.min(height - 4, 10);
        if (this.switchState != null) {
            drawSwitch(graphics, font, label, x, y, width, height, textY, textColor, iconSize, accent, fade, frame);
            return;
        }
        int iconGap = iconSize > 0 && !label.isEmpty() ? 4 : 0;
        // Small buttons, such as the three layer buttons, give their label the padding back.
        int padding = width < 64 ? 4 : 8;
        int room = Math.max(1, width - padding - iconSize - iconGap);
        String clipped = HubDraw.fit(font, label, room);
        int group = iconSize + iconGap + font.width(clipped);
        int left = x + (width - group) / 2;
        if (iconSize > 0) {
            this.icon.draw(graphics, left, y + (height - iconSize) / 2, iconSize, textColor);
        }
        CrystalUi.label(graphics, font, clipped, left + iconSize + iconGap, textY, textColor);
    }

    private void drawSwitch(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width, int height,
            int textY, int textColor, int iconSize, int accent, float fade, float frame) {
        boolean on = this.switchState.getAsBoolean();
        if (this.knob < 0.0F) {
            this.knob = on ? 1.0F : 0.0F;
        }
        this.knob = HubMotion.damp(this.knob, on ? 1.0F : 0.0F, frame, 45.0F);
        int pillHeight = Math.max(4, Math.min(10, height - 6));
        int pillWidth = Math.max(pillHeight + 4, Math.round(pillHeight * 2.1F));
        int pillX = x + width - pillWidth - 5;
        int pillY = y + (height - pillHeight) / 2;
        int track = CrystalTheme.lerp(0xFF2A1238, HubSkin.darken(accent, 0.75F), this.knob);
        if (!this.active) {
            track = 0xFF2A2030;
        }
        graphics.fill(pillX + 1, pillY, pillX + pillWidth - 1, pillY + pillHeight, CrystalTheme.fade(track, fade));
        graphics.fill(pillX, pillY + 1, pillX + pillWidth, pillY + pillHeight - 1, CrystalTheme.fade(track, fade));
        int knobSize = pillHeight - 2;
        int knobX = pillX + 1 + Math.round((pillWidth - knobSize - 2) * this.knob);
        int knobColor = this.active ? CrystalTheme.lerp(0xFFB9A6C8, 0xFFFFFFFF, this.knob) : 0xFF6E6076;
        graphics.fill(knobX, pillY + 1, knobX + knobSize, pillY + 1 + knobSize, CrystalTheme.fade(knobColor, fade));
        if (this.knob > 0.5F && this.active) {
            CrystalUi.outline(graphics, knobX - 1, pillY, knobSize + 2, knobSize + 2,
                    CrystalTheme.fade(CrystalTheme.withAlpha(accent, Math.round(170 * (this.knob - 0.5F) * 2)), fade));
        }
        int textX = x + 6;
        if (iconSize > 0) {
            this.icon.draw(graphics, textX, y + (height - iconSize) / 2, iconSize, textColor);
            textX += iconSize + 4;
        }
        String clipped = HubDraw.fit(font, label, Math.max(1, pillX - textX - 4));
        CrystalUi.label(graphics, font, clipped, textX, textY, textColor);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        MutableComponent message = super.createNarrationMessage();
        if (this.switchState != null) {
            message.append(Component.literal(this.switchState.getAsBoolean() ? ": ON" : ": OFF"));
        }
        return message;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private HubLayout.Rect clip = HubLayout.Rect.EMPTY;

    @Override
    public void clipTo(HubLayout.Rect area) {
        this.clip = area;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && Clippable.inside(this.clip, mouseX, mouseY);
    }
}
