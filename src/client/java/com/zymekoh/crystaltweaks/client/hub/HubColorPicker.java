package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A saturation/brightness square with a hue bar beside it. The square is drawn one column at a time,
 * so it is drawn in a GUI stratum of its own: its columns would otherwise make every element drawn
 * after it test against each of them.
 */
public final class HubColorPicker extends AbstractWidget {
    private static final int[] HUE_STOPS = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    private final IntConsumer onChange;
    private Runnable onRelease;
    private float hue;
    private float saturation;
    private float brightness;
    private boolean lit;

    public HubColorPicker(int x, int y, int width, int height, Component message, int initialColor, IntConsumer onChange) {
        super(x, y, width, height, message);
        this.onChange = onChange;
        setColor(initialColor);
    }

    public HubColorPicker lit() {
        this.lit = true;
        return this;
    }

    public HubColorPicker onRelease(Runnable action) {
        this.onRelease = action;
        return this;
    }

    public void setColor(int color) {
        float[] hsv = rgbToHsv(color);
        // A grey has no hue: keep the bar where it was instead of jumping to red.
        if (hsv[1] > 0.0F && hsv[2] > 0.0F) {
            this.hue = hsv[0];
        }
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }

    public int color() {
        return hsvToArgb(this.hue, this.saturation, this.brightness);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);
        int squareWidth = squareWidth();
        int squareHeight = Math.max(1, this.height);
        int hueColor = hsvToArgb(this.hue, 1.0F, 1.0F);
        HubDraw.isolate(graphics);
        // Two pixels per column on a wide square: half the elements for the same picture.
        int step = squareWidth > 120 ? 2 : 1;
        for (int column = 0; column < squareWidth; column += step) {
            float amount = squareWidth <= 1 ? 1.0F : column / (float) (squareWidth - 1);
            int topColor = CrystalTheme.lerp(0xFFFFFFFF, hueColor, amount);
            graphics.fillGradient(getX() + column, getY(), getX() + Math.min(squareWidth, column + step),
                    getY() + squareHeight, CrystalTheme.fade(topColor, fade), CrystalTheme.fade(0xFF000000, fade));
        }
        int outline = CrystalTheme.fade(this.lit ? skin.sideBorder : skin.border, fade);
        CrystalUi.outline(graphics, getX() - 1, getY() - 1, squareWidth + 2, squareHeight + 2, outline);
        int hueX = hueX();
        for (int segment = 0; segment < 6; segment++) {
            int y0 = getY() + segment * squareHeight / 6;
            int y1 = getY() + (segment + 1) * squareHeight / 6;
            graphics.fillGradient(hueX, y0, hueX + hueBarWidth(), y1, CrystalTheme.fade(HUE_STOPS[segment], fade),
                    CrystalTheme.fade(HUE_STOPS[segment + 1], fade));
        }
        CrystalUi.outline(graphics, hueX - 1, getY() - 1, hueBarWidth() + 2, squareHeight + 2, outline);
        int markerX = getX() + Math.round(this.saturation * Math.max(0, squareWidth - 1));
        int markerY = getY() + Math.round((1.0F - this.brightness) * Math.max(0, squareHeight - 1));
        CrystalUi.outline(graphics, markerX - 2, markerY - 2, 5, 5, CrystalTheme.fade(0xFFFFFFFF, fade));
        CrystalUi.outline(graphics, markerX - 1, markerY - 1, 3, 3, CrystalTheme.fade(0xFF16091F, fade));
        int hueMarkerY = getY() + Math.round(this.hue * Math.max(0, squareHeight - 1));
        CrystalUi.outline(graphics, hueX - 2, hueMarkerY - 1, hueBarWidth() + 4, 3, CrystalTheme.fade(0xFFFFFFFF, fade));
        HubDraw.isolate(graphics);
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
    public void onRelease(MouseButtonEvent event) {
        if (this.onRelease != null) {
            this.onRelease.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.literal(CrystalVisualConfig.toHex(color())));
    }

    private void updateFromMouse(double mouseX, double mouseY) {
        int squareWidth = squareWidth();
        if (mouseX >= hueX() - 1) {
            this.hue = Mth.clamp((float) ((mouseY - getY()) / Math.max(1.0D, this.height - 1.0D)), 0.0F, 1.0F);
        } else {
            this.saturation = Mth.clamp((float) ((mouseX - getX()) / Math.max(1.0D, squareWidth - 1.0D)), 0.0F, 1.0F);
            this.brightness = 1.0F - Mth.clamp((float) ((mouseY - getY()) / Math.max(1.0D, this.height - 1.0D)), 0.0F, 1.0F);
        }
        this.onChange.accept(color());
    }

    private int squareWidth() {
        return Math.max(1, this.width - hueBarWidth() - gap());
    }

    private int hueX() {
        return getX() + squareWidth() + gap();
    }

    private int hueBarWidth() {
        return Math.min(10, Math.max(4, this.width / 9));
    }

    private int gap() {
        return Math.min(6, Math.max(3, this.width / 16));
    }

    static float[] rgbToHsv(int color) {
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
        return new float[] {hue, max == 0.0F ? 0.0F : delta / max, max};
    }

    public static int hsvToArgb(float hue, float saturation, float brightness) {
        float wrapped = hue - (float) Math.floor(hue);
        float scaled = wrapped * 6.0F;
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
        return 0xFF000000 | Math.round(red * 255.0F) << 16 | Math.round(green * 255.0F) << 8 | Math.round(blue * 255.0F);
    }
}
