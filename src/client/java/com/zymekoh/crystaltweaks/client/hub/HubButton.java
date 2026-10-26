package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
        if (GrimoireDraw.active) {
            drawGrimoire(graphics, x, y, width, height, lift, fade, frame, now);
            return;
        }

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
        int border;
        if (this.selected) {
            float shine = HubMotion.breathe(now / 1_000_000_000.0D, 1.6D);
            border = CrystalTheme.lerp(accent, skin.accentBright, 0.35F + 0.4F * shine);
        } else if (!this.active) {
            border = 0x80584A60;
        } else {
            border = CrystalTheme.lerp(HubSkin.tint(skin.borderSoft | 0xFF000000, accent, 0.5F), accent, this.hover);
        }
        float mark = Math.max(this.select, this.hover);
        graphics.fillGradient(x + 1, y, x + width - 1, y + height, CrystalTheme.fade(fillTop, fade), CrystalTheme.fade(fillBottom, fade));
        graphics.fill(x, y + 1, x + 1, y + height - 1, CrystalTheme.fade(fillTop, fade * 0.8F));
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, CrystalTheme.fade(fillTop, fade * 0.8F));
        CrystalUi.outline(graphics, x, y, width, height, CrystalTheme.fade(border, fade * (this.active ? 0.95F : 0.6F)));
        if (this.selected || this.hover > 0.05F) {
            // A short bar of light along the bottom edge, the selected/hovered mark.
            int barWidth = Math.round((width - 6) * mark);
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

        Font font = GrimoireDraw.font();
        int textColor = CrystalTheme.fade(this.active || this.selected
                ? CrystalTheme.lerp(skin.text, 0xFFFFFFFF, lift) : skin.disabled, fade);
        int textY = y + (height - 8) / 2;
        String label = label();
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

    /**
     * The grimoire's look. A switch on a page is its name, a dotted leader and a plate that says
     * on or off; a row that names its value ("Style: Aurora") is the name, a leader and the value on
     * a plate; anything else is a plate with gothic letters.
     */
    private void drawGrimoire(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float lift, float fade, float frame,
            long now) {
        Font font = GrimoireDraw.font();
        String label = label();
        boolean row = this.lit && (this.switchState != null || !this.value.isEmpty()) && width >= 110;
        if (row) {
            drawGrimoireRow(graphics, font, label, x, y, width, height, fade, frame);
        } else {
            int fill;
            int border;
            int text;
            if (this.style == Style.DANGER) {
                fill = CrystalTheme.lerp(GrimoireDraw.CRIMSON_FILL, 0xFF2C0E19, lift);
                border = CrystalTheme.lerp(GrimoireDraw.CRIMSON, 0xFFB8384F, lift);
                text = CrystalTheme.lerp(GrimoireDraw.CRIMSON_TEXT, 0xFFFFFFFF, this.hover);
            } else if (this.selected || this.style == Style.PRIMARY) {
                fill = CrystalTheme.lerp(GrimoireDraw.ACCENT_FILL, 0xFF3A2260, this.hover);
                border = CrystalTheme.lerp(GrimoireDraw.ACCENT, 0xFFD2A8FF, this.hover);
                text = GrimoireDraw.TITLE;
            } else {
                fill = CrystalTheme.lerp(0xFF140E1B, 0xFF1F1629, lift);
                border = CrystalTheme.lerp(GrimoireDraw.BORDER, 0xFF7A48C0, lift);
                text = CrystalTheme.lerp(GrimoireDraw.MUTED, GrimoireDraw.TITLE, lift);
            }
            if (!this.active && !this.selected) {
                fill = 0xFF0F0A13;
                border = GrimoireDraw.RULE;
                text = GrimoireDraw.FAINT;
            }
            if ((this.selected || this.style == Style.PRIMARY) && this.active) {
                float shine = 0.55F + 0.45F * HubMotion.breathe(now / 1_000_000_000.0D, 2.0D);
                HubDraw.halo(graphics, x, y, width, height, GrimoireDraw.ACCENT, 2, (0.18F + 0.22F * this.hover) * shine * fade);
            }
            GrimoireDraw.plate(graphics, x, y, width, height, fill, border, fade);
            int iconSize = this.icon == null ? 0 : Math.min(height - 4, 10);
            int iconGap = iconSize > 0 && !label.isEmpty() ? 4 : 0;
            int padding = width < 64 ? 4 : 8;
            if (iconSize > 0 && !label.isEmpty() && font.width(label) > width - padding - iconSize - iconGap) {
                // The word matters more than the picture: a narrow button keeps its word.
                iconSize = 0;
                iconGap = 0;
            }
            int room = Math.max(1, width - padding - iconSize - iconGap);
            int textColor = CrystalTheme.fade(text, fade);
            // Gothic letters where they fit; the plain ones where a narrow button needs every pixel. Either
            // way scaled to keep clear of the button's border and centred on their ink.
            float gothicScale = GrimoireDraw.gothicFit(height, 1.0F);
            boolean gothic = !label.isEmpty() && GrimoireDraw.gothicWidth(font, label, gothicScale) <= room;
            float textScale = GrimoireDraw.textFit(height, 1.0F);
            String shown = gothic ? label : HubDraw.fit(font, label, Math.round(room / textScale));
            int textWidth = gothic ? GrimoireDraw.gothicWidth(font, label, gothicScale) : Math.round(font.width(shown) * textScale);
            int left = x + (width - iconSize - iconGap - textWidth) / 2;
            if (iconSize > 0) {
                this.icon.draw(graphics, left, y + (height - iconSize) / 2, iconSize, textColor);
            }
            if (gothic) {
                GrimoireDraw.gothic(graphics, font, label, left + iconSize + iconGap, GrimoireDraw.gothicY(y, height, gothicScale), gothicScale,
                        textColor, this.selected ? 0.6F : 0.0F);
            } else {
                GrimoireDraw.text(graphics, font, shown, left + iconSize + iconGap, GrimoireDraw.textY(y, height, textScale), textScale, textColor);
            }
        }
        long sincePress = now - this.pressedAt;
        if (this.pressed && sincePress >= 0L && sincePress < PRESS_FLASH_NANOS) {
            float t = sincePress / (float) PRESS_FLASH_NANOS;
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    CrystalTheme.withAlpha(0xB47CF2, Math.round(90.0F * (1.0F - t) * (1.0F - t) * fade)));
        }
    }

    /** A row on a page: icon, name, dotted leader, and the value or on/off on a plate at the right. */
    private void drawGrimoireRow(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width, int height,
            float fade, float frame) {
        boolean spanish = CrystalUi.spanish();
        String name;
        String value;
        float on;
        if (this.switchState != null) {
            boolean state = this.switchState.getAsBoolean();
            if (this.knob < 0.0F) {
                this.knob = state ? 1.0F : 0.0F;
            }
            this.knob = HubMotion.damp(this.knob, state ? 1.0F : 0.0F, frame, 45.0F);
            on = this.active ? this.knob : 0.0F;
            name = label;
            value = state ? (spanish ? "Encendido" : "On") : (spanish ? "Apagado" : "Off");
        } else {
            on = 0.0F;
            name = this.name;
            value = this.value;
        }
        // The row's rule above it, and a wash under the pointer.
        graphics.fill(x, y - 2, x + width, y - 1, CrystalTheme.fade(GrimoireDraw.RULE, fade));
        if (this.hover > 0.02F) {
            graphics.fill(x, y, x + width, y + height, CrystalTheme.withAlpha(0xB47CF2, Math.round(20 * this.hover * fade)));
        }
        int textColor = CrystalTheme.fade(this.active ? CrystalTheme.lerp(GrimoireDraw.TEXT, 0xFFFFFFFF, this.hover) : GrimoireDraw.FAINT, fade);
        int textY = y + (height - 8) / 2;
        // The plate takes the row's height; its letters are scaled to keep clear of its border.
        int plateHeight = height;
        float valueScale = GrimoireDraw.gothicFit(plateHeight, 1.0F);
        int plateWidth = this.switchState != null
                ? Math.max(GrimoireDraw.gothicWidth(font, spanish ? "Encendido" : "On", valueScale),
                        GrimoireDraw.gothicWidth(font, spanish ? "Apagado" : "Off", valueScale)) + 14
                : Math.min(width / 2, GrimoireDraw.gothicWidth(font, value, valueScale) + 14);
        int plateX = x + width - plateWidth - 1;
        int plateY = y;
        int fill = CrystalTheme.lerp(0xFF120C18, GrimoireDraw.ACCENT_FILL, on);
        int border = CrystalTheme.lerp(CrystalTheme.lerp(GrimoireDraw.BORDER, 0xFF7A48C0, this.hover), GrimoireDraw.ACCENT, on);
        int valueColor = this.active ? CrystalTheme.lerp(this.switchState != null ? GrimoireDraw.FAINT : GrimoireDraw.VALUE,
                GrimoireDraw.TITLE, Math.max(on, this.hover)) : GrimoireDraw.FAINT;
        if (on > 0.05F) {
            HubDraw.halo(graphics, plateX, plateY, plateWidth, plateHeight, GrimoireDraw.ACCENT, 2, 0.2F * on * fade);
        }
        GrimoireDraw.plate(graphics, plateX, plateY, plateWidth, plateHeight, fill, border, fade);
        String shownValue = GrimoireDraw.gothicWidth(font, value, valueScale) <= plateWidth - 10 ? value : null;
        if (shownValue != null) {
            GrimoireDraw.gothicCentered(graphics, font, shownValue, plateX + plateWidth / 2.0F, GrimoireDraw.gothicY(plateY, plateHeight, valueScale),
                    valueScale, CrystalTheme.fade(valueColor, fade), on * 0.5F);
        } else {
            float textScale = GrimoireDraw.textFit(plateHeight, 1.0F);
            String fitted = HubDraw.fit(font, value, Math.round((plateWidth - 10) / textScale));
            GrimoireDraw.text(graphics, font, fitted, plateX + (plateWidth - font.width(fitted) * textScale) / 2.0F,
                    GrimoireDraw.textY(plateY, plateHeight, textScale), textScale, CrystalTheme.fade(valueColor, fade));
        }
        int textX = x + 2;
        int iconSize = this.icon == null ? 0 : Math.min(height - 4, 10);
        if (iconSize > 0) {
            this.icon.draw(graphics, textX, y + (height - iconSize) / 2, iconSize, textColor);
            textX += iconSize + 4;
        }
        String clipped = HubDraw.fit(font, name, Math.max(1, plateX - textX - 6));
        CrystalUi.label(graphics, font, clipped, textX, textY, textColor);
        GrimoireDraw.dots(graphics, textX + font.width(clipped) + 4, plateX - 4, textY + 6,
                CrystalTheme.fade(GrimoireDraw.DOTS, fade));
    }

    /** The message as text, and its name and value when it reads "Name: value"; worked out when it changes, not every frame. */
    private Component shownMessage;
    private String label = "";
    private String name = "";
    private String value = "";

    private String label() {
        Component message = getMessage();
        if (message != this.shownMessage) {
            this.shownMessage = message;
            this.label = message.getString();
            int split = this.label.indexOf(": ");
            this.name = split > 0 ? this.label.substring(0, split) : this.label;
            this.value = split > 0 ? this.label.substring(split + 2) : "";
        }
        return this.label;
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
