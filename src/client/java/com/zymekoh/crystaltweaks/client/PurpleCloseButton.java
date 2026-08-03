package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

final class PurpleCloseButton extends AbstractButton {
    private final Consumer<PurpleCloseButton> action;
    private float hoverProgress;
    private long lastFrameTime = System.currentTimeMillis();

    PurpleCloseButton(int x, int y, int width, int height, Component message, Consumer<PurpleCloseButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        long elapsed = Mth.clamp(now - this.lastFrameTime, 0L, 50L);
        this.lastFrameTime = now;

        float target = this.isHoveredOrFocused() ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed / 65.0F);
        this.hoverProgress += (target - this.hoverProgress) * response;

        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        int red = lerp(66, 126, this.hoverProgress);
        int green = lerp(18, 40, this.hoverProgress);
        int blue = lerp(96, 176, this.hoverProgress);
        graphics.fillGradient(
                x + 2,
                y,
                x + width - 2,
                y + height,
                argb(220, red, green, blue),
                argb(230, red / 2, green / 2, blue / 2));
        graphics.fill(x, y + 2, x + width, y + height - 2, argb(95, red, green, blue));

        int pulse = 205 + Math.round(45.0F * (0.5F + 0.5F * (float) Math.sin(now / 190.0F)));
        drawOutline(graphics, x + 1, y + 1, width - 2, height - 2, argb(220, 210, 112, pulse));
        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                x + width / 2,
                y + (height - Minecraft.getInstance().font.lineHeight) / 2,
                this.active ? 0xFFF7EDFF : 0xFFAA98B2);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static int lerp(int start, int end, float progress) {
        return start + Math.round((end - start) * progress);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void drawOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
