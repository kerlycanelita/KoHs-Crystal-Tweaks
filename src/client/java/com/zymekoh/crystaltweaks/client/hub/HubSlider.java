package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The hub's slider: the filled part of the track shows the value, the handle glows while held, and
 * the label stays shadowed wherever the handle passes under it. On a side panel it is lit by the
 * glow like everything else there.
 */
public final class HubSlider extends AbstractSliderButton implements Clippable {
    private final DoubleUnaryOperator toValue;
    private final DoubleConsumer onChange;
    private final DoubleFunction<String> formatter;
    private Runnable onRelease;
    private boolean lit;
    private float hover;
    private long lastFrame = System.nanoTime();

    private HubSlider(int x, int y, int width, int height, double position, DoubleUnaryOperator toValue,
            DoubleConsumer onChange, DoubleFunction<String> formatter) {
        super(x, y, width, height, Component.empty(), position);
        this.toValue = toValue;
        this.onChange = onChange;
        this.formatter = formatter;
        updateMessage();
    }

    /** A linear slider from {@code minimum} to {@code maximum}. */
    public static HubSlider linear(int x, int y, int width, int height, double minimum, double maximum, double current,
            DoubleConsumer onChange, DoubleFunction<String> formatter) {
        double span = maximum - minimum;
        double position = Mth.clamp((current - minimum) / span, 0.0D, 1.0D);
        return new HubSlider(x, y, width, height, position, t -> minimum + t * span, onChange, formatter);
    }

    /**
     * A slider whose travel is squared, so the low end, where the fine values live, gets most of
     * it: on a 0-10 s range the first half of the track covers 0-2.5 s.
     */
    public static HubSlider curved(int x, int y, int width, int height, double minimum, double maximum, double current,
            DoubleConsumer onChange, DoubleFunction<String> formatter) {
        double span = maximum - minimum;
        double position = Math.sqrt(Mth.clamp((current - minimum) / span, 0.0D, 1.0D));
        return new HubSlider(x, y, width, height, position, t -> minimum + t * t * span, onChange, formatter);
    }

    public HubSlider lit() {
        this.lit = true;
        return this;
    }

    /** Called when the player lets go: the moment to save, not every step of the drag. */
    public HubSlider onRelease(Runnable action) {
        this.onRelease = action;
        return this;
    }

    public double currentValue() {
        return this.toValue.applyAsDouble(this.value);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(this.formatter.apply(currentValue())));
    }

    @Override
    protected void applyValue() {
        this.onChange.accept(currentValue());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        if (this.onRelease != null) {
            this.onRelease.run();
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        long now = System.nanoTime();
        float frame = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        boolean hot = isHoveredOrFocused() && this.active;
        this.hover = HubMotion.damp(this.hover, hot ? 1.0F : 0.0F, frame, 55.0F);
        float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        if (GrimoireDraw.active && width >= 110) {
            drawGrimoire(graphics, x, y, width, height, fade);
            return;
        }
        this.trackWidth = 0;
        int accent = this.lit ? skin.litAccent() : skin.accent;
        int fill = this.lit ? skin.litControlFill(this.hover) : CrystalTheme.lerp(skin.controlFill, skin.controlHover, this.hover);
        graphics.fill(x, y, x + width, y + height, CrystalTheme.fade(fill, fade));
        int filled = (int) Math.round(this.value * Math.max(0, width - 2));
        graphics.fillGradient(x + 1, y + 1, x + 1 + filled, y + height - 1,
                CrystalTheme.fade(CrystalTheme.withAlpha(accent, 120), fade),
                CrystalTheme.fade(CrystalTheme.withAlpha(HubSkin.darken(accent, 0.5F), 70), fade));
        int outline = CrystalTheme.lerp(HubSkin.tint(skin.borderSoft | 0xFF000000, accent, 0.45F), accent, this.hover);
        CrystalUi.outline(graphics, x, y, width, height, CrystalTheme.fade(outline, fade * 0.9F));
        int handleWidth = 6;
        int handleX = x + (int) Math.round(this.value * Math.max(0, width - handleWidth));
        int handle = CrystalTheme.lerp(HubSkin.tint(0xFFDCC8EC, accent, 0.35F), 0xFFFFFFFF, this.hover);
        if (this.hover > 0.05F) {
            CrystalUi.outline(graphics, handleX - 1, y, handleWidth + 2, height,
                    CrystalTheme.fade(CrystalTheme.withAlpha(accent, Math.round(150 * this.hover)), fade));
        }
        graphics.fill(handleX, y + 1, handleX + handleWidth, y + height - 1, CrystalTheme.fade(handle, fade));
        Font font = GrimoireDraw.font();
        String text = HubDraw.fit(font, getMessage().getString(), Math.max(1, width - 8));
        int textX = x + (width - font.width(text)) / 2;
        CrystalUi.label(graphics, font, text, textX, y + (height - 8) / 2,
                CrystalTheme.fade(this.active ? skin.text : skin.disabled, fade), true);
    }

    /**
     * The grimoire's look: the name, a dotted leader, a thin track with a round knob and the value
     * in gothic letters. The pointer moves the knob along the track as drawn, not the whole row.
     */
    private void drawGrimoire(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float fade) {
        Font font = GrimoireDraw.font();
        splitMessage();
        String name = this.shownName;
        String value = this.shownValue;
        // One column for the values, so the tracks of a page line up.
        int valueWidth = Math.max(GrimoireDraw.gothicWidth(font, "10.0 s", 1.0F), GrimoireDraw.gothicWidth(font, value, 1.0F)) + 2;
        this.trackWidth = Mth.clamp(Math.round(width * 0.4F), 40, 120);
        this.trackX = x + width - valueWidth - 6 - this.trackWidth;
        int centerY = y + height / 2;
        graphics.fill(x, y - 2, x + width, y - 1, CrystalTheme.fade(GrimoireDraw.RULE, fade));
        if (this.hover > 0.02F) {
            graphics.fill(x, y, x + width, y + height, CrystalTheme.withAlpha(0xB47CF2, Math.round(20 * this.hover * fade)));
        }
        int textColor = CrystalTheme.fade(this.active ? CrystalTheme.lerp(GrimoireDraw.TEXT, 0xFFFFFFFF, this.hover) : GrimoireDraw.FAINT, fade);
        int textY = y + (height - 8) / 2;
        String clipped = HubDraw.fit(font, name, Math.max(1, this.trackX - x - 10));
        CrystalUi.label(graphics, font, clipped, x + 2, textY, textColor);
        GrimoireDraw.dots(graphics, x + 2 + font.width(clipped) + 4, this.trackX - 6, textY + 6, CrystalTheme.fade(GrimoireDraw.DOTS, fade));
        // The track, the part already filled and the knob.
        int knobX = this.trackX + (int) Math.round(this.value * this.trackWidth);
        graphics.fill(this.trackX, centerY, this.trackX + this.trackWidth, centerY + 1, CrystalTheme.fade(GrimoireDraw.TRACK, fade));
        graphics.fill(this.trackX, centerY - 1, knobX, centerY + 2, CrystalTheme.fade(GrimoireDraw.ACCENT, fade));
        HubDraw.glowDisc(graphics, knobX, centerY, 6 + Math.round(2 * this.hover), GrimoireDraw.ACCENT, (0.45F + 0.35F * this.hover) * fade);
        HubDraw.disc(graphics, knobX + 0.5F, centerY + 0.5F, 3.0F, CrystalTheme.fade(this.active ? GrimoireDraw.TITLE : 0xFF6E6076, fade));
        if (!value.isEmpty()) {
            int valueX = x + width - 2 - GrimoireDraw.gothicWidth(font, value, 1.0F);
            GrimoireDraw.gothic(graphics, font, value, valueX, textY, 1.0F,
                    CrystalTheme.fade(CrystalTheme.lerp(GrimoireDraw.VALUE, 0xFFFFFFFF, this.hover), fade), 0.0F);
        }
    }

    /** The message's name and value ("Glow: 55%"), worked out when it changes, not every frame. */
    private Component shownMessage;
    private String shownName = "";
    private String shownValue = "";

    private void splitMessage() {
        Component message = getMessage();
        if (message != this.shownMessage) {
            this.shownMessage = message;
            String text = message.getString();
            int split = text.indexOf(": ");
            this.shownName = split > 0 ? text.substring(0, split) : text;
            this.shownValue = split > 0 ? text.substring(split + 2) : "";
        }
    }

    /** Where the track was last drawn; its width is 0 when the slider is drawn the plain way. */
    private int trackX;
    private int trackWidth;

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        fromTrack(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        super.onDrag(event, dragX, dragY);
        fromTrack(event.x());
    }

    private void fromTrack(double mouseX) {
        if (this.trackWidth > 0) {
            setValue((mouseX - this.trackX) / this.trackWidth);
        }
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
