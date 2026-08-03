package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

final class ColorPickerWidget extends AbstractWidget {
    private final IntConsumer onChange;
    private float hue;
    private float saturation;
    private float brightness;

    ColorPickerWidget(int x, int y, int width, int height, Component message, int initialColor, IntConsumer onChange) {
        super(x, y, width, height, message);
        this.onChange = onChange;
        setColor(initialColor);
    }

    void setColor(int color) {
        float[] hsv = rgbToHsv(color);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }

    int color() {
        return hsvToArgb(this.hue, this.saturation, this.brightness);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int squareWidth = squareWidth();
        int squareHeight = Math.max(1, this.height);
        int hueColor = hsvToArgb(this.hue, 1.0F, 1.0F);

        for (int column = 0; column < squareWidth; column++) {
            float amount = squareWidth <= 1 ? 1.0F : column / (float) (squareWidth - 1);
            int topColor = lerpRgb(0xFFFFFFFF, hueColor, amount);
            graphics.fillGradient(
                    this.getX() + column,
                    this.getY(),
                    this.getX() + column + 1,
                    this.getY() + squareHeight,
                    topColor,
                    0xFF000000);
        }
        drawOutline(graphics, this.getX(), this.getY(), squareWidth, squareHeight, 0xB9D49AE8);

        int hueX = hueX();
        int[] hueStops = {
                0xFFFF0000,
                0xFFFFFF00,
                0xFF00FF00,
                0xFF00FFFF,
                0xFF0000FF,
                0xFFFF00FF,
                0xFFFF0000
        };
        for (int segment = 0; segment < 6; segment++) {
            int y0 = this.getY() + segment * squareHeight / 6;
            int y1 = this.getY() + (segment + 1) * squareHeight / 6;
            graphics.fillGradient(hueX, y0, hueX + hueBarWidth(), y1, hueStops[segment], hueStops[segment + 1]);
        }
        drawOutline(graphics, hueX, this.getY(), hueBarWidth(), squareHeight, 0xB9D49AE8);

        int markerX = this.getX() + Math.round(this.saturation * Math.max(0, squareWidth - 1));
        int markerY = this.getY() + Math.round((1.0F - this.brightness) * Math.max(0, squareHeight - 1));
        drawOutline(graphics, markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        drawOutline(graphics, markerX - 1, markerY - 1, 3, 3, 0xFF16091F);

        int hueMarkerY = this.getY() + Math.round(this.hue * Math.max(0, squareHeight - 1));
        drawOutline(graphics, hueX - 1, hueMarkerY - 1, hueBarWidth() + 2, 3, 0xFFFFFFFF);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        updateFromMouse(event.x(), event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        updateFromMouse(event.x(), event.y());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
        output.add(NarratedElementType.USAGE, Component.literal(CrystalVisualConfig.toHex(color())));
    }

    private void updateFromMouse(double mouseX, double mouseY) {
        int squareWidth = squareWidth();
        if (mouseX >= hueX()) {
            this.hue = Mth.clamp((float) ((mouseY - this.getY()) / Math.max(1.0D, this.height - 1.0D)), 0.0F, 1.0F);
        } else {
            this.saturation = Mth.clamp((float) ((mouseX - this.getX()) / Math.max(1.0D, squareWidth - 1.0D)), 0.0F, 1.0F);
            this.brightness = 1.0F - Mth.clamp(
                    (float) ((mouseY - this.getY()) / Math.max(1.0D, this.height - 1.0D)),
                    0.0F,
                    1.0F);
        }
        this.onChange.accept(color());
    }

    private int squareWidth() {
        return Math.max(1, this.width - hueBarWidth() - gap());
    }

    private int hueX() {
        return this.getX() + squareWidth() + gap();
    }

    private int hueBarWidth() {
        return Math.min(10, Math.max(1, this.width / 5));
    }

    private int gap() {
        return Math.min(5, Math.max(1, this.width / 8));
    }

    private static float[] rgbToHsv(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta % 6.0F) / 6.0F;
        } else if (max == green) {
            hue = ((blue - red) / delta + 2.0F) / 6.0F;
        } else {
            hue = ((red - green) / delta + 4.0F) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max == 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static int hsvToArgb(float hue, float saturation, float brightness) {
        float wrappedHue = hue - (float) Math.floor(hue);
        float scaled = wrappedHue * 6.0F;
        int sector = (int) scaled;
        float fraction = scaled - sector;
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - fraction * saturation);
        float t = brightness * (1.0F - (1.0F - fraction) * saturation);
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> {
                red = brightness;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = brightness;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = brightness;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = brightness;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = brightness;
            }
            default -> {
                red = brightness;
                green = p;
                blue = q;
            }
        }
        return 0xFF000000
                | Math.round(red * 255.0F) << 16
                | Math.round(green * 255.0F) << 8
                | Math.round(blue * 255.0F);
    }

    private static int lerpRgb(int from, int to, float amount) {
        int red = Mth.lerpInt(amount, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int green = Mth.lerpInt(amount, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int blue = Mth.lerpInt(amount, from & 0xFF, to & 0xFF);
        return 0xFF000000 | red << 16 | green << 8 | blue;
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
