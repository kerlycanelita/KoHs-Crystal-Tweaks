package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The KoHs purple button. Optional extras: a pixel icon before the label, a selected state for tabs
 * and layer pickers, and an on/off switch whose knob slides to the new state. Every animation is
 * timed in real time and drawn inside the widget's own bounds, so the hitbox is always what you see.
 */
final class PurpleCloseButton extends AbstractButton {
    enum Icon { NONE, CRYSTAL, GLOW, SOUND, GEAR, ARROW_LEFT, ARROW_RIGHT }

    private static final long PRESS_FLASH_NANOS = 260_000_000L;

    private final Consumer<PurpleCloseButton> action;
    private float hoverProgress;
    private float selectProgress;
    private float switchProgress = -1.0F;
    private long lastFrameNanos = System.nanoTime();
    private long pressedAtNanos = Long.MIN_VALUE;
    private Icon icon = Icon.NONE;
    private BooleanSupplier switchState;
    private boolean selected;

    PurpleCloseButton(int x, int y, int width, int height, Component message, Consumer<PurpleCloseButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    PurpleCloseButton icon(Icon icon) {
        this.icon = icon == null ? Icon.NONE : icon;
        return this;
    }

    /** Turns the button into a switch: the label on the left, the state as a sliding knob. */
    PurpleCloseButton switchOf(BooleanSupplier state) {
        this.switchState = state;
        return this;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    boolean isSelected() {
        return this.selected;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.pressedAtNanos = System.nanoTime();
        this.action.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float elapsed = Mth.clamp((now - this.lastFrameNanos) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrameNanos = now;
        float response = 1.0F - (float) Math.exp(-elapsed / 65.0F);
        float hoverTarget = this.isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hoverProgress += (hoverTarget - this.hoverProgress) * response;
        this.selectProgress += ((this.selected ? 1.0F : 0.0F) - this.selectProgress) * response;
        float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);

        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        float lift = Math.max(this.hoverProgress, this.selectProgress * 0.85F);
        int red = lerp(66, 126, lift);
        int green = lerp(18, 40, lift);
        int blue = lerp(96, 176, lift);
        if (!this.active && !this.selected) {
            red = 48;
            green = 26;
            blue = 60;
        }
        graphics.fillGradient(x + 2, y, x + width - 2, y + height,
                argb(Math.round(220 * fade), red, green, blue),
                argb(Math.round(230 * fade), red / 2, green / 2, blue / 2));
        graphics.fill(x, y + 2, x + width, y + height - 2, argb(Math.round(95 * fade), red, green, blue));

        long nowMillis = now / 1_000_000L;
        int pulse = 205 + Math.round(45.0F * (0.5F + 0.5F * (float) Math.sin(nowMillis / 190.0F)));
        int border = this.selected
                ? CrystalTheme.fade(CrystalTheme.lerp(0xFFD9A2FF, 0xFFF6E2FF, 0.5F + 0.5F * (float) Math.sin(nowMillis / 260.0F)), fade)
                : argb(Math.round((this.active ? 220 : 120) * fade), 210, 112, pulse);
        CrystalUi.outline(graphics, x + 1, y + 1, width - 2, height - 2, border);

        // A short flash from the press, so a click is felt even when nothing else moves.
        if (now - this.pressedAtNanos < PRESS_FLASH_NANOS) {
            float t = (now - this.pressedAtNanos) / (float) PRESS_FLASH_NANOS;
            int flash = Math.round(120.0F * (1.0F - t) * (1.0F - t) * fade);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, argb(flash, 245, 215, 255));
        }

        Font font = Minecraft.getInstance().font;
        int textColor = CrystalTheme.fade(this.active || this.selected
                ? CrystalTheme.lerp(CrystalTheme.TEXT, 0xFFFFFFFF, lift) : CrystalTheme.TEXT_DISABLED, fade);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        String label = getMessage().getString();

        if (this.switchState != null) {
            drawSwitch(graphics, font, label, x, y, width, height, textY, textColor, fade, response);
            return;
        }

        int iconWidth = this.icon == Icon.NONE ? 0 : 7;
        int iconGap = iconWidth > 0 && !label.isEmpty() ? 4 : 0;
        int room = Math.max(1, width - 8 - iconWidth - iconGap);
        String clipped = font.width(label) > room ? font.plainSubstrByWidth(label, room) : label;
        int groupWidth = iconWidth + iconGap + font.width(clipped);
        int left = x + (width - groupWidth) / 2;
        if (iconWidth > 0) {
            CrystalUi.icon(graphics, this.icon, left, y + (height - 7) / 2, textColor);
        }
        CrystalUi.label(graphics, font, clipped, left + iconWidth + iconGap, textY, textColor);
    }

    private void drawSwitch(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
            int width, int height, int textY, int textColor, float fade, float response) {
        boolean on = this.switchState.getAsBoolean();
        if (this.switchProgress < 0.0F) {
            this.switchProgress = on ? 1.0F : 0.0F;
        }
        this.switchProgress += ((on ? 1.0F : 0.0F) - this.switchProgress) * Math.min(1.0F, response * 1.6F);

        int pillHeight = Math.max(4, Math.min(10, height - 6));
        int pillWidth = Math.max(pillHeight + 4, Math.round(pillHeight * 2.1F));
        int pillX = x + width - pillWidth - 5;
        int pillY = y + (height - pillHeight) / 2;
        int track = CrystalTheme.lerp(0xFF2A1238, 0xFF7C3FB8, this.switchProgress);
        if (!this.active) {
            track = 0xFF2A2030;
        }
        graphics.fill(pillX + 1, pillY, pillX + pillWidth - 1, pillY + pillHeight, CrystalTheme.fade(track, fade));
        graphics.fill(pillX, pillY + 1, pillX + pillWidth, pillY + pillHeight - 1, CrystalTheme.fade(track, fade));
        int knob = pillHeight - 2;
        int knobX = pillX + 1 + Math.round((pillWidth - knob - 2) * this.switchProgress);
        int knobColor = this.active
                ? CrystalTheme.lerp(0xFFB9A6C8, 0xFFFFFFFF, this.switchProgress) : 0xFF6E6076;
        graphics.fill(knobX, pillY + 1, knobX + knob, pillY + 1 + knob, CrystalTheme.fade(knobColor, fade));
        if (this.switchProgress > 0.5F && this.active) {
            // The knob glows while on, so the state reads by brightness as well as by position.
            CrystalUi.outline(graphics, knobX - 1, pillY, knob + 2, knob + 2,
                    CrystalTheme.fade(CrystalTheme.withAlpha(0xE6B8FF, Math.round(160 * (this.switchProgress - 0.5F) * 2)), fade));
        }

        int iconWidth = this.icon == Icon.NONE ? 0 : 7;
        int textX = x + 7;
        if (iconWidth > 0) {
            CrystalUi.icon(graphics, this.icon, textX, y + (height - 7) / 2, textColor);
            textX += iconWidth + 4;
        }
        int room = Math.max(1, pillX - textX - 4);
        String clipped = font.width(label) > room ? font.plainSubstrByWidth(label, room) : label;
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

    private static int lerp(int start, int end, float progress) {
        return start + Math.round((end - start) * progress);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return Mth.clamp(alpha, 0, 255) << 24 | red << 16 | green << 8 | blue;
    }
}
