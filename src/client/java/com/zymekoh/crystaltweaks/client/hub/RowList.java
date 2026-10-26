package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.FormattedCharSequence;

/**
 * A column of rows that scrolls inside a viewport: the content of a side panel or a drawer.
 *
 * <p>Rows hold widgets, headings, wrapped notes or anything drawn by a {@link Painter}. Every frame
 * {@link #place} puts each widget exactly where its row is drawn, and {@link #render} draws the
 * widgets itself, clipped to the viewport: a row scrolling in shows a little more of itself each
 * frame and fades up as it comes, instead of popping in whole. Its controls take clicks only on the
 * part that shows (see {@link Clippable}), so the hitbox is always what is drawn.</p>
 *
 * <p>Scrolling is smooth: the wheel moves a target, by as much as the wheel turned, and the drawn
 * offset glides after it. When the rows first appear, each one fades and slides in a little after
 * the one above.</p>
 *
 * <p>Rows between {@link #openBranch} and {@link #closeBranch} belong to the switch above them: they
 * slide out from under it when it turns on and fold back under it when it turns off, and the rows
 * below glide to make room or close in. Nothing is rebuilt for it.</p>
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
        /** The live icon before a section title, or null. */
        HubButton.Icon icon;
        final List<FormattedCharSequence> note;
        final List<Cell> cells = new ArrayList<>();
        /** When the row shows: null for always, else its branches' switches, all on. */
        final BooleanSupplier open;
        /** How far out the row is, 0 folded under its parent to 1 in full, eased. */
        float shown;
        int y;

        Row(int height, int indent, String heading, List<FormattedCharSequence> note, BooleanSupplier open) {
            this.height = height;
            this.indent = indent;
            this.heading = heading;
            this.note = note;
            this.open = open;
            this.shown = wanted() ? 1.0F : 0.0F;
        }

        boolean wanted() {
            return this.open == null || this.open.getAsBoolean();
        }

        /** The height it takes this frame. */
        int takes() {
            return Math.round(this.height * this.shown);
        }
    }

    private static final long ROW_STAGGER_NANOS = 32_000_000L;
    /** How quickly a branch unfolds or folds: each frame takes 1 - e^(-ms/75) of the way. */
    private static final float BRANCH_TAU_MILLIS = 75.0F;
    private static final long ROW_FADE_NANOS = 230_000_000L;
    /** How long the drawn offset takes to settle on the target, in milliseconds. */
    private static final float SCROLL_TAU_MILLIS = 95.0F;

    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Deque<BooleanSupplier> branches = new ArrayDeque<>();
    private final int width;
    private final int gap;
    private Rect viewport = Rect.EMPTY;
    private int contentHeight;
    private int scrollTarget;
    /** Where {@link #follow} is gliding to while the rows settle, or -1. */
    private int following = -1;
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

    /** A section title: the words and a line to the right edge. */
    public RowList heading(String text) {
        return heading(text, null);
    }

    /** A section title led by a live icon from the game: the words, and a line to the right edge. */
    public RowList heading(String text, HubButton.Icon icon) {
        Row row = new Row(13, 0, text, null, this.branches.peek());
        row.icon = icon;
        add(row);
        return this;
    }

    /** Muted text wrapped to the list's width. */
    public RowList note(Font font, String text, int indent) {
        List<FormattedCharSequence> lines = CrystalUi.wrap(font, text, this.width - indent);
        add(new Row(lines.size() * GrimoireDraw.line(font), indent, null, lines, this.branches.peek()));
        return this;
    }

    public RowList space(int height) {
        add(new Row(Math.max(0, height), 0, null, null, this.branches.peek()));
        return this;
    }

    /**
     * The rows added until {@link #closeBranch} show only while {@code open} says so, inside any
     * branch already open: a switch's own settings, under it.
     */
    public RowList openBranch(BooleanSupplier open) {
        BooleanSupplier outer = this.branches.peek();
        this.branches.push(outer == null ? open : () -> outer.getAsBoolean() && open.getAsBoolean());
        return this;
    }

    public RowList closeBranch() {
        this.branches.pop();
        return this;
    }

    /** A row of {@code height} pixels, filled by the cells added next. */
    public RowList row(int height) {
        return row(height, 0);
    }

    /** A row set in by {@code indent} pixels: one that a switch above it unfolds. */
    public RowList row(int height, int indent) {
        add(new Row(height, indent, null, null, this.branches.peek()));
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
        this.rows.add(row);
        stack();
    }

    /** Stacks the rows by how far out each one is: folded rows take no room, and no gap either. */
    private void stack() {
        int y = 0;
        boolean first = true;
        for (Row row : this.rows) {
            int gapAbove = first ? 0 : Math.round(this.gap * row.shown);
            row.y = y + gapAbove;
            y = row.y + row.takes();
            if (row.shown > 0.0F) {
                first = false;
            }
        }
        this.contentHeight = y;
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

    /** Rows fade in from {@code at}; {@code direction} 1 slides them in from the left, -1 from the right. */
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

    /**
     * Glides to {@code target} as the rows settle: a branch just unfolded makes the room it needs
     * frame by frame, so a plain {@link #scrollTo} would stop short of it. The wheel cancels it.
     */
    public void follow(int target) {
        this.following = target;
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
        this.following = -1;
        // A notched wheel turns by whole steps; a smooth wheel or a touchpad by fractions of one.
        double turned = Math.max(-3.0D, Math.min(3.0D, amount));
        this.scrollTarget -= (int) Math.round(turned * Math.max(8, step));
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
        float response = 1.0F - (float) Math.exp(-frameMillis / BRANCH_TAU_MILLIS);
        boolean moving = false;
        for (Row row : this.rows) {
            float target = row.wanted() ? 1.0F : 0.0F;
            if (row.shown != target) {
                row.shown += (target - row.shown) * response;
                if (Math.abs(target - row.shown) < 0.004F) {
                    row.shown = target;
                }
                moving = true;
            }
        }
        if (moving) {
            stack();
            clampScroll();
        }
        if (this.following >= 0) {
            this.scrollTarget = this.following;
            clampScroll();
            if (!moving) {
                this.following = -1;
            }
        }
        this.scroll = HubMotion.damp(this.scroll, this.scrollTarget, frameMillis, SCROLL_TAU_MILLIS);
        if (Math.abs(this.scroll - this.scrollTarget) < 0.3F) {
            this.scroll = this.scrollTarget;
        }
        int offset = Math.round(this.scroll);
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            float fade = rowFade(now, index);
            int slide = Math.round((1.0F - fade) * 9.0F) * -this.slideDirection;
            int top = this.viewport.y() + row.y - offset;
            int takes = row.takes();
            // A row coming out of its branch slides down from under the row above it.
            int y = top - (row.height - takes);
            float edge = edgeFade(top, Math.max(1, takes));
            Rect clip = takes >= row.height ? this.viewport : intersect(this.viewport, new Rect(this.viewport.x() - 2, top,
                    this.viewport.width() + 4, takes));
            for (Cell cell : row.cells) {
                if (cell.widget() == null) {
                    continue;
                }
                AbstractWidget widget = cell.widget();
                widget.setX(this.viewport.x() + row.indent + cell.x() + slide);
                widget.setY(y);
                widget.visible = takes > 1 && edge > 0.02F && fade > 0.05F && alpha > 0.05F;
                widget.setAlpha(Math.max(0.0F, Math.min(1.0F, fade * alpha * edge * Math.min(1.0F, row.shown * 1.4F))));
                if (widget instanceof Clippable clippable) {
                    clippable.clipTo(clip);
                }
            }
        }
    }

    /**
     * How much of a row shows, eased: 1 well inside the viewport, falling to 0 as it scrolls out of
     * either edge. Rows fade as they leave and come up as they arrive.
     */
    private float edgeFade(int y, int height) {
        int top = this.viewport.y();
        int bottom = this.viewport.bottom();
        int shown = Math.min(y + height, bottom) - Math.max(y, top);
        if (shown <= 0 || height <= 0) {
            return 0.0F;
        }
        float zone = Math.max(6.0F, Math.min(16.0F, this.viewport.height() / 6.0F));
        float center = y + height / 2.0F;
        float fromEdge = Math.min(center - top, bottom - center) + height / 2.0F;
        float soft = HubMotion.clamp01(fromEdge / zone);
        float part = HubMotion.clamp01(shown / (float) height);
        return HubMotion.smoothstep(0.0F, 1.0F, Math.min(soft, 0.35F + 0.65F * part));
    }

    private static Rect intersect(Rect a, Rect b) {
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return right <= x || bottom <= y ? Rect.EMPTY : new Rect(x, y, right - x, bottom - y);
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
        render(graphics, font, now, alpha, accent, muted, line, -10_000, -10_000, 0.0F);
    }

    /**
     * Draws the rows and their widgets, clipped to the viewport.
     *
     * @param mouseX where the pointer is, or far off screen while something covers the list
     */
    public void render(GuiGraphicsExtractor graphics, Font font, long now, float alpha, int accent, int muted, int line,
            int mouseX, int mouseY, float partialTick) {
        if (alpha <= 0.02F || this.viewport.isEmpty()) {
            return;
        }
        int offset = Math.round(this.scroll);
        graphics.enableScissor(this.viewport.x() - 2, this.viewport.y(), this.viewport.right() + 2, this.viewport.bottom());
        int trunkTop = -1;
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            int takes = row.takes();
            if (takes <= 0) {
                continue;
            }
            float fade = rowFade(now, index) * alpha;
            int top = this.viewport.y() + row.y - offset;
            if (top > this.viewport.bottom() || top + takes < this.viewport.y()) {
                trunkTop = row.indent > 0 ? trunkTop : -1;
                continue;
            }
            fade *= edgeFade(top, takes) * Math.min(1.0F, row.shown * 1.4F);
            boolean folding = takes < row.height;
            if (folding) {
                graphics.enableScissor(this.viewport.x() - 2, Math.max(top, this.viewport.y()), this.viewport.right() + 2,
                        Math.min(top + takes, this.viewport.bottom()));
            }
            // A folding row's content sits higher, under the row above, by what is still folded.
            int y = top - (row.height - takes);
            int x = this.viewport.x() + row.indent;
            if (row.heading != null) {
                int textY = y + 2;
                int textX = this.viewport.x();
                if (row.icon != null) {
                    row.icon.draw(graphics, textX, textY - 1, 10, CrystalTheme.fade(0xFFFFFFFF, fade));
                    textX += 13;
                }
                int headingWidth;
                if (GrimoireDraw.active) {
                    GrimoireDraw.gothic(graphics, font, row.heading, textX, textY, 1.0F, CrystalTheme.fade(GrimoireDraw.VALUE, fade), 0.0F);
                    headingWidth = GrimoireDraw.gothicWidth(font, row.heading, 1.0F);
                } else {
                    CrystalUi.label(graphics, font, row.heading, textX, textY, CrystalTheme.fade(muted, fade));
                    headingWidth = font.width(row.heading);
                }
                int lineX = textX + 3 + headingWidth;
                if (lineX < this.viewport.right() - 2) {
                    graphics.fill(lineX, textY + 4, this.viewport.right(), textY + 5,
                            CrystalTheme.fade(CrystalTheme.withAlpha(line, 90), fade));
                }
            }
            if (row.note != null) {
                int lineY = y;
                for (FormattedCharSequence text : row.note) {
                    HubDraw.line(graphics, font, text, x, lineY, CrystalTheme.fade(muted, fade));
                    lineY += GrimoireDraw.line(font);
                }
            }
            for (Cell cell : row.cells) {
                if (cell.painter() != null) {
                    cell.painter().paint(graphics, font, x + cell.x(), y, cell.width(), row.height, fade);
                }
            }
            // The row's controls, at the opacity place() gave them, inside the row's own clip.
            for (Cell cell : row.cells) {
                if (cell.widget() != null && cell.widget().visible) {
                    cell.widget().extractRenderState(graphics, mouseX, mouseY, partialTick);
                }
            }
            if (folding) {
                graphics.disableScissor();
            }
            if (row.indent > 0 && !row.cells.isEmpty()) {
                // The branch: a trunk down from the row above and an arm into this one.
                int trunkX = this.viewport.x() + Math.max(2, row.indent / 2 - 1);
                int armY = Math.min(top + takes - 1, y + row.height / 2);
                int from = trunkTop >= 0 ? trunkTop : top - this.gap - 2;
                int branchColor = CrystalTheme.fade(CrystalTheme.withAlpha(line, 150), fade);
                graphics.fill(trunkX, from, trunkX + 1, armY + 1, branchColor);
                graphics.fill(trunkX, armY, x - 1, armY + 1, branchColor);
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
                    CrystalTheme.fade(0xD0110C17, alpha), 0x00110C17);
        }
        if (this.scroll < max - 0.5F) {
            graphics.fillGradient(view.x(), view.bottom() - fade, view.right(), view.bottom(), 0x00110C17,
                    CrystalTheme.fade(0xE0110C17, alpha));
        }
    }
}
