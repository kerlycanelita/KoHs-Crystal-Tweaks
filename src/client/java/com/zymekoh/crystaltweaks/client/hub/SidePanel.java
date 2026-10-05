package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One of the two side panels: Colors on the left, Glow on the right.
 *
 * <p>It unfolds from the crystal: a line of light runs out from the stage to the panel's inner edge,
 * the glass opens outward behind a bright front that throws sparks, the title comes up, and the
 * rows slide in one after another from the crystal's side. Its controls only take clicks once it is
 * fully open. Its surface and border are lit by the crystal's glow (see {@link HubSkin}).</p>
 */
public final class SidePanel {
    public enum Side { LEFT, RIGHT }

    /** What a click on the reset button did: armed it, or confirmed it. */
    public enum ResetClick { NONE, ARMED, RESET }

    private static final long OPEN_NANOS = 380_000_000L;
    private static final long TITLE_DELAY_NANOS = 170_000_000L;
    private static final long ROWS_DELAY_NANOS = 240_000_000L;
    /** How long the reset button waits for its confirming second click. */
    private static final long ARM_NANOS = 3_000_000_000L;

    private final Side side;
    private Rect rect = Rect.EMPTY;
    private Rect viewport = Rect.EMPTY;
    private Rect title = Rect.EMPTY;
    private String label = "";
    private RowList list;
    private long unfoldAt = Long.MIN_VALUE;
    private boolean shown;
    private float resetHover;
    /** The reset button as drawn: as wide as its words, laid out with the title. */
    private Rect resetButton = Rect.EMPTY;
    private long armedAt = Long.MIN_VALUE;

    public SidePanel(Side side) {
        this.side = side;
    }

    public Side side() {
        return this.side;
    }

    public void setGeometry(Rect panel, HubLayout layout) {
        this.rect = panel;
        this.viewport = layout.panelViewport(panel);
        this.title = layout.panelTitle(panel);
        if (this.list != null) {
            this.list.setViewport(this.viewport);
        }
    }

    public Rect rect() {
        return this.rect;
    }

    public Rect viewport() {
        return this.viewport;
    }

    /** Where the panel's animated icon goes, left of its title. */
    public Rect iconRect() {
        int size = Math.max(8, this.title.height() - 4);
        return new Rect(this.title.x() + 4, this.title.y() + (this.title.height() - size) / 2, size, size);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Replaces the rows, keeping the scroll position when the same panel is rebuilt. */
    public void setList(RowList list, boolean keepScroll) {
        int scroll = this.list != null && keepScroll ? this.list.scrollTarget() : 0;
        boolean scrolled = this.list != null && keepScroll && this.list.scrolledByPlayer();
        this.list = list;
        list.setViewport(this.viewport);
        list.scrollTo(scroll, true);
        list.markScrolled(scrolled);
        list.reveal(this.unfoldAt == Long.MIN_VALUE ? Long.MIN_VALUE / 2 : this.unfoldAt + ROWS_DELAY_NANOS,
                this.side == Side.LEFT ? 1 : -1);
    }

    public RowList list() {
        return this.list;
    }

    /** Starts unfolding at {@code at}. */
    public void unfold(long at) {
        this.unfoldAt = at;
        this.shown = true;
        if (this.list != null) {
            this.list.reveal(at + ROWS_DELAY_NANOS, this.side == Side.LEFT ? 1 : -1);
        }
    }

    /** Shows the panel at once, fully open: after a resize or a return from another screen. */
    public void showOpen() {
        this.unfoldAt = System.nanoTime() - OPEN_NANOS * 4;
        this.shown = true;
        if (this.list != null) {
            this.list.reveal(Long.MIN_VALUE / 2, this.side == Side.LEFT ? 1 : -1);
        }
    }

    public void hide() {
        this.shown = false;
        if (this.list != null) {
            this.list.hide();
        }
    }

    public boolean shown() {
        return this.shown;
    }

    public float openProgress(long now) {
        if (!this.shown) {
            return 0.0F;
        }
        return HubMotion.easeOutCubic(HubMotion.progress(now - this.unfoldAt, OPEN_NANOS));
    }

    public boolean fullyOpen(long now) {
        return this.shown && now - this.unfoldAt >= OPEN_NANOS;
    }

    public boolean contains(double mouseX, double mouseY) {
        return this.shown && this.rect.contains(mouseX, mouseY);
    }

    public boolean overReset(double mouseX, double mouseY) {
        return this.shown && this.resetButton.contains(mouseX, mouseY);
    }

    /**
     * A click on the reset button. The first click only arms it, and it says so; a second click
     * within three seconds resets. A slip of the mouse never wipes a colour the player spent time on.
     */
    public ResetClick resetClick(double mouseX, double mouseY) {
        if (!overReset(mouseX, mouseY)) {
            return ResetClick.NONE;
        }
        long now = System.nanoTime();
        if (armed(now)) {
            this.armedAt = Long.MIN_VALUE;
            return ResetClick.RESET;
        }
        this.armedAt = now;
        return ResetClick.ARMED;
    }

    private boolean armed(long now) {
        return this.armedAt != Long.MIN_VALUE && now - this.armedAt < ARM_NANOS;
    }

    public void place(long now, float frameMillis, boolean interactive) {
        if (this.list == null) {
            return;
        }
        if (!this.shown) {
            this.list.hide();
            return;
        }
        this.list.place(now, frameMillis, 1.0F, interactive && fullyOpen(now));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, int step) {
        return this.shown && this.list != null && this.list.mouseScrolled(mouseX, mouseY, amount, step);
    }

    public boolean listOverflows() {
        return this.list != null && this.list.overflows();
    }

    public boolean scrolledByPlayer() {
        return this.list != null && this.list.scrolledByPlayer();
    }

    /**
     * Draws the frame, the unfolding light, the title and the rows' own decoration.
     *
     * @param originX where the crystal is, for the line of light the panel unfolds along
     */
    public void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY,
            int originX, int originY) {
        if (!this.shown || this.rect.isEmpty()) {
            return;
        }
        HubSkin skin = HubSkin.current();
        double seconds = now / 1_000_000_000.0D;
        float open = openProgress(now);
        boolean left = this.side == Side.LEFT;
        int innerEdge = left ? this.rect.right() : this.rect.x();
        if (open < 1.0F) {
            drawBeam(graphics, now, originX, originY, innerEdge, skin);
        }
        int revealed = Math.max(1, Math.round(this.rect.width() * open));
        int revealX = left ? this.rect.right() - revealed : this.rect.x();
        // The glow around the panel: stronger while the glow is being changed.
        float influence = skin.influence;
        if (open > 0.6F && influence > 0.04F) {
            HubDraw.isolate(graphics);
            HubDraw.halo(graphics, this.rect.x(), this.rect.y(), this.rect.width(), this.rect.height(), skin.halo,
                    3 + Math.round(3 * influence), 0.18F + 0.32F * influence);
            HubDraw.isolate(graphics);
        }
        graphics.enableScissor(revealX, this.rect.y() - 1, revealX + revealed, this.rect.bottom() + 1);
        HubDraw.glass(graphics, this.rect.x(), this.rect.y(), this.rect.width(), this.rect.height(), skin.sideTop,
                skin.sideBottom, skin.sideBorder, 1.0F);
        // A slow sheen sweeping down the glass, in the glow's colour.
        float sheen = (float) ((seconds * 0.22D + (left ? 0.0D : 0.5D)) % 1.0D);
        int sheenY = this.rect.y() + Math.round(sheen * (this.rect.height() + 40)) - 20;
        graphics.fillGradient(this.rect.x() + 1, Math.max(this.rect.y() + 1, sheenY - 14), this.rect.right() - 1,
                Math.min(this.rect.bottom() - 1, sheenY), 0x00FFFFFF,
                CrystalTheme.withAlpha(skin.halo, Math.round(10 + 22 * influence)));
        graphics.disableScissor();
        if (open < 1.0F) {
            drawFront(graphics, now, left ? revealX : revealX + revealed - 1, skin);
        }
        float titleIn = HubMotion.easeOutCubic(HubMotion.progress(now, this.unfoldAt, TITLE_DELAY_NANOS, 240_000_000L));
        if (titleIn > 0.02F) {
            drawTitle(graphics, font, titleIn, mouseX, mouseY, frameMillis, skin);
        }
        if (this.list != null) {
            // The rows' controls only answer the pointer once the panel has finished opening.
            boolean ready = fullyOpen(now);
            this.list.render(graphics, font, now, open, skin.litAccent(), skin.muted, skin.sideBorder,
                    ready ? mouseX : -10_000, ready ? mouseY : -10_000, 0.0F);
        }
    }

    /**
     * The turning mouse wheel that says the rows scroll, over the panel's controls. Shown until the
     * player has scrolled a panel once; only where the rows really continue past the bottom.
     */
    public void renderScrollHint(GuiGraphicsExtractor graphics, Font font, long now) {
        if (fullyOpen(now) && this.list != null && this.list.overflows() && !this.list.scrolledByPlayer()) {
            drawScrollHint(graphics, font, now / 1_000_000_000.0D, HubSkin.current());
        }
    }

    private void drawBeam(GuiGraphicsExtractor graphics, long now, int originX, int originY, int innerEdge, HubSkin skin) {
        float t = HubMotion.progress(now - this.unfoldAt, 200_000_000L);
        if (t <= 0.0F || t >= 1.0F && HubMotion.progress(now - this.unfoldAt, OPEN_NANOS) >= 1.0F) {
            return;
        }
        float reach = HubMotion.easeOutCubic(t);
        float fade = 1.0F - HubMotion.progress(now - this.unfoldAt - 200_000_000L, 180_000_000L);
        int endX = Math.round(originX + (innerEdge - originX) * reach);
        int y = Math.round(originY + (this.rect.centerY() - originY) * reach);
        HubDraw.line(graphics, originX, originY, endX, y, 3.0F, CrystalTheme.withAlpha(skin.halo, Math.round(70 * fade)));
        HubDraw.line(graphics, originX, originY, endX, y, 1.0F, CrystalTheme.withAlpha(0xFFFFFF, Math.round(220 * fade)));
        HubDraw.sparkle(graphics, endX, y, 3, CrystalTheme.withAlpha(skin.accentBright, Math.round(255 * fade)));
    }

    private void drawFront(GuiGraphicsExtractor graphics, long now, int x, HubSkin skin) {
        int top = this.rect.y();
        int bottom = this.rect.bottom();
        graphics.fill(x - 1, top, x + 2, bottom, CrystalTheme.withAlpha(skin.halo, 90));
        graphics.fill(x, top, x + 1, bottom, 0xF0FFFFFF);
        // Sparks thrown off the front as it moves.
        for (int index = 0; index < 7; index++) {
            float jitter = HubMotion.hash(index * 31L + now / 40_000_000L);
            int y = top + Math.round(jitter * this.rect.height());
            int dx = Math.round((HubMotion.hash(index * 17L + now / 40_000_000L) - 0.5F) * 8.0F);
            graphics.fill(x + dx, y, x + dx + 1, y + 1, CrystalTheme.withAlpha(skin.accentBright, 200));
        }
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Font font, float alpha, int mouseX, int mouseY, float frameMillis,
            HubSkin skin) {
        long now = System.nanoTime();
        Rect icon = iconRect();
        int textX = icon.right() + 5;
        int textY = this.title.y() + (this.title.height() - 8) / 2 + 1;
        // The reset button first: as wide as its words where the title leaves room, the arrow alone where not.
        boolean spanish = CrystalUi.spanish();
        boolean armed = armed(now);
        String full = armed ? (spanish ? "↺ ¿Seguro?" : "↺ Sure?") : (spanish ? "↺ Restablecer" : "↺ Reset");
        String brief = armed ? "↺ ?" : "↺";
        int room = this.title.right() - 4 - (textX + Math.min(font.width(this.label), 60) + 6);
        String text = font.width(full) + 8 <= room ? full : brief;
        int buttonWidth = font.width(text) + 8;
        int buttonHeight = Math.max(10, this.title.height() - 6);
        this.resetButton = new Rect(this.title.right() - 4 - buttonWidth, this.title.y() + (this.title.height() - buttonHeight) / 2,
                buttonWidth, buttonHeight);
        CrystalUi.label(graphics, font, HubDraw.fit(font, this.label, Math.max(10, this.resetButton.x() - textX - 4)), textX, textY,
                CrystalTheme.fade(skin.title, alpha), true);
        int lineY = this.title.bottom() - 1;
        int lineLeft = this.rect.x() + 4;
        int lineRight = this.rect.right() - 4;
        graphics.fillGradient(lineLeft, lineY, lineRight, lineY + 1, CrystalTheme.fade(CrystalTheme.withAlpha(skin.sideBorder, 150), alpha),
                CrystalTheme.fade(CrystalTheme.withAlpha(skin.sideBorder, 60), alpha));
        boolean hovered = this.resetButton.contains(mouseX, mouseY);
        this.resetHover = HubMotion.damp(this.resetHover, hovered ? 1.0F : 0.0F, frameMillis, 60.0F);
        int top;
        int border;
        int textColor;
        if (armed) {
            // Armed: red, breathing, until it is confirmed or the three seconds run out.
            float pulse = HubMotion.breathe(now / 1_000_000_000.0D, 0.8D);
            top = CrystalTheme.lerp(0xD0601420, 0xE08A1E30, pulse);
            border = CrystalTheme.lerp(0xFFFF5A6E, 0xFFFFC2C8, pulse * 0.6F);
            textColor = 0xFFFFE4E8;
        } else {
            top = CrystalTheme.lerp(skin.controlFill, skin.controlHover, this.resetHover);
            border = CrystalTheme.lerp(HubSkin.tint(skin.borderSoft | 0xFF000000, skin.litAccent(), 0.4F), skin.litAccent(), this.resetHover);
            textColor = CrystalTheme.lerp(skin.muted, 0xFFFFFFFF, this.resetHover);
        }
        HubDraw.glass(graphics, this.resetButton.x(), this.resetButton.y(), this.resetButton.width(), this.resetButton.height(), top,
                HubSkin.darken(top, 0.6F), border, alpha);
        CrystalUi.label(graphics, font, text, this.resetButton.x() + 4, this.resetButton.y() + (this.resetButton.height() - 8) / 2 + 1,
                CrystalTheme.fade(textColor, alpha));
    }

    private void drawScrollHint(GuiGraphicsExtractor graphics, Font font, double seconds, HubSkin skin) {
        float pulse = HubMotion.breathe(seconds, 2.4D);
        int centerX = this.viewport.centerX();
        int bottom = this.viewport.bottom() - 4;
        String text = CrystalUi.spanish() ? "Desliza" : "Scroll";
        int pillWidth = Math.max(40, font.width(text) + 26);
        int pillHeight = 30;
        int pillX = centerX - pillWidth / 2;
        int pillY = bottom - pillHeight;
        HubDraw.isolate(graphics);
        HubDraw.glass(graphics, pillX, pillY, pillWidth, pillHeight, 0xE01A0A2A, 0xE00B0414,
                CrystalTheme.withAlpha(skin.litAccent(), Math.round(150 + 80 * pulse)), 1.0F);
        HubDraw.wheelHint(graphics, centerX, pillY + 12, seconds, 1.0F, false, skin.litAccent());
        CrystalUi.centered(graphics, font, text, centerX, pillY + pillHeight - 9, CrystalTheme.withAlpha(skin.text, 220));
        HubDraw.isolate(graphics);
    }
}
