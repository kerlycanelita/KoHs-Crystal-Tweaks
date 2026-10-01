package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.FormattedCharSequence;

/**
 * A column of rows that scrolls inside a viewport: the content of a side panel or a drawer.
 *
 * <p>Rows hold widgets, headings, wrapped notes or anything drawn by a {@link Painter}. Every frame
 * {@link #place} puts each widget exactly where its row is drawn, so the hitbox always matches what
 * is on screen; a widget whose row is not wholly inside the viewport is hidden rather than clipped,
 * because a half-drawn control would still take clicks where the player cannot see it.</p>
 *
 * <p>Scrolling is smooth: the wheel moves a target and the drawn offset follows it on a short
 * spring. When the rows first appear, each one fades and slides in a little after the one above.</p>
 */
public final class RowList {
    /** Draws a row's own decoration into its bounds. */
    public interface Painter {
        void paint(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, float alpha);
    }

    private record Cell(AbstractWidget widget, Painter painter, int x, int width) {
    }

    private static final class Row {
        final int height;
        final int indent;
        final String heading;
        final List<FormattedCharSequence> note;
        final List<Cell> cells = new ArrayList<>();
        int y;

        Row(int height, int indent, String heading, List<FormattedCharSequence> note) {
            this.height = height;
            this.indent = indent;
            this.heading = heading;
            this.note = note;
        }
    }

    private static final long ROW_STAGGER_NANOS = 32_000_000L;
    private static final long ROW_FADE_NANOS = 230_000_000L;

    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final int width;
    private final int gap;
    private Rect viewport = Rect.EMPTY;
    private int contentHeight;
    private int scrollTarget;
    private float scroll;
    private long revealAt;
    private int slideDirection = 1;
    private boolean scrolledByPlayer;

    public RowList(int width, int gap) {
        this.width = Math.max(1, width);
        this.gap = Math.max(0, gap);
    }

    public int width() {
        return this.width;
    }

    // ------------------------------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------------------------------

    /** A section title: a small diamond, the words, and a line to the right edge. */
    public RowList heading(String text) {
        add(new Row(11, 0, text, null));
        return this;
    }

    /** Muted text wrapped to the list's width. */
    public RowList note(Font font, String text, int indent) {
        List<FormattedCharSequence> lines = CrystalUi.wrap(font, text, this.width - indent);
        add(new Row(lines.size() * (font.lineHeight + 1), indent, null, lines));
        return this;
    }

    public RowList space(int height) {
        add(new Row(Math.max(0, height), 0, null, null));
        return this;
    }

    /** A row of {@code height} pixels, filled by the cells added next. */
    public RowList row(int height) {
        return row(height, 0);
    }

    /** A row set in by {@code indent} pixels: one that a switch above it unfolds. */
    public RowList row(int height, int indent) {
        add(new Row(height, indent, null, null));
        return this;
    }

    /** A widget across the whole current row. */
    public <T extends AbstractWidget> T full(T widget) {
        Row row = this.rows.get(this.rows.size() - 1);
        return cell(widget, 0, this.width - row.indent);
    }

    /** A widget at {@code x} (from the row's own left edge, after any indent), {@code width} wide. */
    public <T extends AbstractWidget> T cell(T widget, int x, int cellWidth) {
        Row row = this.rows.get(this.rows.size() - 1);
        widget.setWidth(Math.max(1, cellWidth));
        widget.setHeight(row.height);
        row.cells.add(new Cell(widget, null, x, cellWidth));
        this.widgets.add(widget);
        return widget;
    }

    public RowList paint(Painter painter, int x, int cellWidth) {
        this.rows.get(this.rows.size() - 1).cells.add(new Cell(null, painter, x, cellWidth));
        return this;
    }

    private void add(Row row) {
        row.y = this.contentHeight + (this.rows.isEmpty() ? 0 : this.gap);
        this.contentHeight = row.y + row.height;
        this.rows.add(row);
    }

    public List<AbstractWidget> widgets() {
        return this.widgets;
    }

    public int contentHeight() {
        return this.contentHeight;
    }

    // ------------------------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------------------------

    public void setViewport(Rect viewport) {
        this.viewport = viewport;
        clampScroll();
        this.scroll = Math.min(this.scroll, maxScroll());
    }

    public Rect viewport() {
        return this.viewport;
    }

    /** Rows fade in from {@code at}; {@code direction} is the side they slide from, -1 left, 1 right. */
    public void reveal(long at, int direction) {
        this.revealAt = at;
        this.slideDirection = direction;
    }

    public int maxScroll() {
        return Math.max(0, this.contentHeight - this.viewport.height());
    }

    public int scrollTarget() {
        return this.scrollTarget;
    }

    public void scrollTo(int target, boolean instant) {
        this.scrollTarget = target;
        clampScroll();
        if (instant) {
            this.scroll = this.scrollTarget;
        }
    }

    /** True once the player has scrolled this list themselves. */
    public boolean scrolledByPlayer() {
        return this.scrolledByPlayer;
    }

    public void markScrolled(boolean scrolled) {
        this.scrolledByPlayer = scrolled;
    }

    public boolean overflows() {
        return maxScroll() > 0;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, int step) {
        if (!this.viewport.contains(mouseX, mouseY) || maxScroll() <= 0 || amount == 0.0D) {
            return false;
        }
        int before = this.scrollTarget;
        this.scrollTarget -= (int) Math.signum(amount) * Math.max(8, step);
        clampScroll();
        if (this.scrollTarget != before) {
            this.scrolledByPlayer = true;
        }
        return true;
    }

    private void clampScroll() {
        this.scrollTarget = Math.max(0, Math.min(maxScroll(), this.scrollTarget));
    }

    private float rowFade(long now, int index) {
        long elapsed = now - this.revealAt - index * ROW_STAGGER_NANOS;
        return HubMotion.easeOutCubic(HubMotion.progress(elapsed, ROW_FADE_NANOS));
    }

    /**
     * Puts every widget where its row is this frame and decides whether it can be seen and clicked.
     *
     * @param alpha       the list's overall opacity
     * @param interactive unused while drawing: the screen withholds the pointer and the clicks
     *                    while anything covers the list, so its rows can stay drawn under it
     */
    public void place(long now, float frameMillis, float alpha, boolean interactive) {
        this.scroll = HubMotion.damp(this.scroll, this.scrollTarget, frameMillis, 55.0F);
        if (Math.abs(this.scroll - this.scrollTarget) < 0.3F) {
            this.scroll = this.scrollTarget;
        }
        int offset = Math.round(this.scroll);
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            float fade = rowFade(now, index);
            int slide = Math.round((1.0F - fade) * 9.0F) * -this.slideDirection;
            int y = this.viewport.y() + row.y - offset;
            boolean inside = y >= this.viewport.y() && y + row.height <= this.viewport.bottom();
            for (Cell cell : row.cells) {
                if (cell.widget() == null) {
                    continue;
                }
                AbstractWidget widget = cell.widget();
                widget.setX(this.viewport.x() + row.indent + cell.x() + slide);
                widget.setY(y);
                widget.visible = inside && fade > 0.05F && alpha > 0.05F;
                widget.setAlpha(Math.max(0.0F, Math.min(1.0F, fade * alpha)));
            }
        }
    }

    /** Hides every widget: the list is gone or covered. */
    public void hide() {
        for (AbstractWidget widget : this.widgets) {
            widget.visible = false;
        }
    }

    /**
     * Draws what is not a widget: headings, notes, painted cells, the branch lines of indented rows,
     * the scrollbar and the fades at the edges where rows continue.
     */
    public void render(GuiGraphicsExtractor graphics, Font font, long now, float alpha, int accent, int muted, int line) {
        if (alpha <= 0.02F || this.viewport.isEmpty()) {
            return;
        }
        int offset = Math.round(this.scroll);
        graphics.enableScissor(this.viewport.x() - 2, this.viewport.y(), this.viewport.right() + 2, this.viewport.bottom());
        int trunkTop = -1;
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            float fade = rowFade(now, index) * alpha;
            int y = this.viewport.y() + row.y - offset;
            if (y > this.viewport.bottom() || y + row.height < this.viewport.y()) {
                trunkTop = row.indent > 0 ? trunkTop : -1;
                continue;
            }
            int x = this.viewport.x() + row.indent;
            if (row.heading != null) {
                int textY = y + 2;
                HubDraw.diamond(graphics, this.viewport.x() + 2, textY + 3, 2, CrystalTheme.fade(accent, fade));
                CrystalUi.label(graphics, font, row.heading, this.viewport.x() + 8, textY, CrystalTheme.fade(muted, fade));
                int lineX = this.viewport.x() + 11 + font.width(row.heading);
                if (lineX < this.viewport.right() - 2) {
                    graphics.fill(lineX, textY + 4, this.viewport.right(), textY + 5,
                            CrystalTheme.fade(CrystalTheme.withAlpha(line, 90), fade));
                }
            }
            if (row.note != null) {
                int lineY = y;
                for (FormattedCharSequence text : row.note) {
                    HubDraw.line(graphics, font, text, x, lineY, CrystalTheme.fade(muted, fade));
                    lineY += font.lineHeight + 1;
                }
            }
            for (Cell cell : row.cells) {
                if (cell.painter() != null) {
                    cell.painter().paint(graphics, font, x + cell.x(), y, cell.width(), row.height, fade);
                }
            }
            if (row.indent > 0 && !row.cells.isEmpty()) {
                // The branch: a trunk down from the row above and an arm into this one.
                int trunkX = this.viewport.x() + Math.max(2, row.indent / 2 - 1);
                int armY = y + row.height / 2;
                int from = trunkTop >= 0 ? trunkTop : y - this.gap - 2;
                graphics.fill(trunkX, from, trunkX + 1, armY + 1, CrystalTheme.fade(CrystalTheme.withAlpha(line, 150), fade));
                graphics.fill(trunkX, armY, x - 2, armY + 1, CrystalTheme.fade(CrystalTheme.withAlpha(line, 150), fade));
                trunkTop = armY;
            } else {
                trunkTop = -1;
            }
        }
        graphics.disableScissor();
        drawScroll(graphics, alpha, accent);
    }

    private void drawScroll(GuiGraphicsExtractor graphics, float alpha, int accent) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        Rect view = this.viewport;
        int trackX = view.right() + 2;
        int trackHeight = view.height();
        int thumbHeight = Math.max(10, trackHeight * trackHeight / Math.max(1, this.contentHeight));
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = view.y() + Math.round(travel * this.scroll / max);
        graphics.fill(trackX, view.y(), trackX + 2, view.bottom(), CrystalTheme.fade(0x70301638, alpha));
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, CrystalTheme.fade(accent, alpha));
        int fade = Math.min(12, Math.max(4, view.height() / 8));
        if (this.scroll > 0.5F) {
            graphics.fillGradient(view.x(), view.y(), view.right(), view.y() + fade,
                    CrystalTheme.fade(0xB0120618, alpha), 0x00120618);
        }
        if (this.scroll < max - 0.5F) {
            graphics.fillGradient(view.x(), view.bottom() - fade, view.right(), view.bottom(), 0x00120618,
                    CrystalTheme.fade(0xC0120618, alpha));
        }
    }
}
