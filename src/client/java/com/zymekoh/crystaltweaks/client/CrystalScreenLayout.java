package com.zymekoh.crystaltweaks.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the settings screen as a pure function of the window size and of what each tab
 * shows. The screen builds its widgets from these rectangles and the layout tests check the very
 * same ones, so the two can never drift apart.
 *
 * <p>Two modes: with room to spare every group of controls gets a card with a title set into its
 * border; on smaller windows the cards stay but lose their titles, so no row has to scroll for a
 * label.</p>
 */
public final class CrystalScreenLayout {
    public static final int MAX_PANEL_WIDTH = 500;
    public static final int MAX_PANEL_HEIGHT = 290;
    /** Content height from which the cards carry titles. */
    public static final int LEGEND_MIN_CONTENT_HEIGHT = 196;

    public enum Tab { COLORS, GLOW, SOUNDS, TWEAKS }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public boolean overlaps(Rect other) {
            return this.x < other.right() && other.x < right()
                    && this.y < other.bottom() && other.y < bottom();
        }

        public boolean contains(double px, double py) {
            return px >= this.x && px < right() && py >= this.y && py < bottom();
        }

        public boolean inside(Rect outer) {
            return this.x >= outer.x && this.y >= outer.y
                    && right() <= outer.right() && bottom() <= outer.bottom();
        }
    }

    /** One control, where it sits before scrolling, and the row and card it belongs to. */
    public record Slot(String name, Rect rect, int row, int group) { }

    /** A card drawn behind a group of rows. {@code key} names its title. */
    public record Group(String key, Rect rect, int firstRow) { }

    /** What a tab shows right now, since some rows come and go with the settings. */
    public record Content(boolean enemy, boolean flashScalable, boolean customGlow, boolean ghostAvailable) { }

    public record Rows(List<Slot> slots, List<Group> groups, int bottom, int textY) {
        public Slot slot(String name) {
            for (Slot slot : this.slots) {
                if (slot.name().equals(name)) {
                    return slot;
                }
            }
            return null;
        }
    }

    public final int screenWidth;
    public final int screenHeight;
    public final Rect panel;
    public final int headerHeight;
    public final int footerHeight;
    public final Rect content;
    public final int optionsX;
    public final int optionsWidth;
    public final Rect preview;
    public final int previewNoteHeight;
    public final int controlHeight;
    public final int rowGap;
    public final int groupGap;
    public final int cardPadding;
    public final int firstRowInset;
    public final boolean legends;
    public final boolean small;
    public final int titleY;
    public final int tabY;

    private CrystalScreenLayout(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        int horizontalMargin = clamp(this.screenWidth / 28, 4, 18);
        int verticalMargin = clamp(this.screenHeight / 28, 4, 14);
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, this.screenWidth - horizontalMargin * 2));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(1, this.screenHeight - verticalMargin * 2));
        this.panel = new Rect((this.screenWidth - panelWidth) / 2, (this.screenHeight - panelHeight) / 2,
                panelWidth, panelHeight);
        this.small = panelHeight < 190;

        this.headerHeight = Math.min(this.small ? 42 : 50, Math.max(1, panelHeight / 2));
        this.footerHeight = Math.min(this.small ? 25 : 31, Math.max(1, (panelHeight - this.headerHeight) / 3));
        int padding = clamp(panelWidth / 48, 5, 11);
        int contentX = this.panel.x() + padding;
        int contentY = this.panel.y() + this.headerHeight;
        int contentWidth = Math.max(1, panelWidth - padding * 2);
        int contentHeight = Math.max(1, this.panel.bottom() - this.footerHeight - contentY);
        this.content = new Rect(contentX, contentY, contentWidth, contentHeight);

        this.rowGap = Math.min(this.small ? 3 : 5, Math.max(0, (contentHeight - 4) / 8));
        int fourRowHeight = Math.max(1, (contentHeight - this.rowGap * 3) / 4);
        this.controlHeight = Math.min(this.small ? 14 : 18, fourRowHeight);

        int previewGap = contentWidth < 390 ? 6 : 10;
        boolean previewFits = contentWidth >= 300 && contentHeight >= 86;
        int previewWidth = previewFits ? clamp(Math.round(contentWidth * 0.24F), 82, 118) : 0;
        this.optionsX = contentX;
        this.optionsWidth = previewWidth > 0 ? Math.max(1, contentWidth - previewWidth - previewGap) : contentWidth;
        this.previewNoteHeight = previewWidth > 0 && contentHeight >= 125 ? 31 : 0;
        this.preview = previewWidth > 0
                ? new Rect(contentX + contentWidth - previewWidth, contentY, previewWidth,
                        Math.max(1, contentHeight - this.previewNoteHeight))
                : new Rect(0, contentY, 0, 0);

        this.legends = contentHeight >= LEGEND_MIN_CONTENT_HEIGHT && this.optionsWidth >= 180;
        // A card reaches this far past its rows; the gap between groups leaves daylight between two
        // cards, and with titles also room for the title that sits in the next card's top edge.
        this.cardPadding = this.legends ? 3 : 2;
        this.groupGap = this.legends ? 14 : this.rowGap + 2;
        this.firstRowInset = this.legends ? 10 : this.cardPadding;
        this.titleY = this.panel.y() + (this.small ? 5 : 7);
        this.tabY = this.panel.y() + this.headerHeight - this.controlHeight - (this.small ? 3 : 5);
    }

    public static CrystalScreenLayout fit(int screenWidth, int screenHeight) {
        return new CrystalScreenLayout(screenWidth, screenHeight);
    }

    public int rowStep() {
        return this.controlHeight + this.rowGap;
    }

    /** The tab buttons, centred in the header. */
    public List<Rect> tabs(int count) {
        int gap = this.panel.width() < 330 ? 3 : 6;
        int available = Math.max(count, this.panel.width() - 18);
        int tabWidth = Math.max(1, Math.min(112, (available - gap * (count - 1)) / count));
        int groupWidth = tabWidth * count + gap * (count - 1);
        int x = this.panel.x() + (this.panel.width() - groupWidth) / 2;
        List<Rect> tabs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tabs.add(new Rect(x + (tabWidth + gap) * i, this.tabY, tabWidth, this.controlHeight));
        }
        return tabs;
    }

    /** Reset and Done, centred in the footer. */
    public List<Rect> footerButtons() {
        int footerTop = this.panel.bottom() - this.footerHeight;
        int height = Math.max(1, Math.min(18, this.footerHeight - 4));
        int gap = 6;
        int width = Math.min(104, Math.max(1, (this.panel.width() - 18 - gap) / 2));
        int x = this.panel.x() + (this.panel.width() - width * 2 - gap) / 2;
        int y = footerTop + (this.footerHeight - height) / 2;
        return List.of(new Rect(x, y, width, height), new Rect(x + width + gap, y, width, height));
    }

    public Rows rows(Tab tab, Content state) {
        Builder rows = new Builder();
        int width = this.optionsWidth;
        switch (tab) {
            case COLORS -> {
                rows.group(state.enemy() ? "colors.enemy" : "colors");
                int gap = width < 250 ? 2 : 5;
                int layer = Math.max(1, (width - gap * 2) / 3);
                rows.row(this.controlHeight)
                        .add("layer.outer", this.optionsX, layer)
                        .add("layer.inner", this.optionsX + layer + gap, layer)
                        .add("layer.core", this.optionsX + (layer + gap) * 2, width - (layer + gap) * 2);
                rows.row(this.controlHeight).add("hex", this.optionsX, Math.min(94, width));
                rows.row(Math.min(48, Math.max(12, this.content.height() / 4))).add("picker", this.optionsX, width);
                rows.group("animation");
                rows.row(this.controlHeight).add("rotation", this.optionsX, width);
                rows.row(this.controlHeight).add("floating", this.optionsX, width);
                rows.group(state.enemy() ? "profile.enemy" : "profile");
                rows.row(this.controlHeight).add("profile", this.optionsX, width);
            }
            case GLOW -> {
                rows.group("glow");
                rows.row(this.controlHeight).add("power", this.optionsX, width);
                rows.row(this.controlHeight).add("reflections", this.optionsX, width);
                rows.group("flash");
                int arrow = Math.max(1, Math.min(width / 5, this.controlHeight + 6));
                rows.row(this.controlHeight)
                        .add("flash.previous", this.optionsX, arrow)
                        .add("flash.style", this.optionsX + arrow + 3, Math.max(1, width - (arrow + 3) * 2))
                        .add("flash.next", this.optionsX + width - arrow, arrow);
                if (state.flashScalable()) {
                    rows.row(this.controlHeight).add("flash.size", this.optionsX, width);
                }
                rows.group("glow.color");
                boolean hexBeside = state.customGlow() && width >= 200;
                int hexWidth = Math.min(94, width);
                if (hexBeside) {
                    rows.row(this.controlHeight)
                            .add("glow.color", this.optionsX, width - hexWidth - 5)
                            .add("hex", this.optionsX + width - hexWidth, hexWidth);
                } else {
                    rows.row(this.controlHeight).add("glow.color", this.optionsX, width);
                    if (state.customGlow()) {
                        rows.row(this.controlHeight).add("hex", this.optionsX, hexWidth);
                    }
                }
                if (state.customGlow()) {
                    // The picker takes what the rows above left, and the panel scrolls when that is
                    // not enough rather than squeezing it down to a strip.
                    int used = rows.cursor - this.content.y() + this.rowGap + this.cardPadding;
                    rows.row(clamp(this.content.height() - used, 40, 120)).add("picker", this.optionsX, width);
                }
            }
            case SOUNDS -> {
                rows.group("sound.file");
                rows.row(this.controlHeight).add("sound.toggle", this.optionsX, width);
                rows.row(this.controlHeight).add("sound.file", this.optionsX, width);
                rows.group("sound.playback");
                rows.row(this.controlHeight).add("sound.volume", this.optionsX, width);
                rows.row(this.controlHeight).add("sound.speed", this.optionsX, width);
                rows.textLine(9);
            }
            case TWEAKS -> {
                rows.group("helpers");
                if (state.ghostAvailable()) {
                    rows.row(this.controlHeight).add("ghost", this.optionsX, width);
                }
                rows.row(this.controlHeight).add("safe", this.optionsX, width);
                rows.group("compatibility");
                rows.row(this.controlHeight).add("monitor", this.optionsX, width);
                rows.row(this.controlHeight).add("rescan", this.optionsX, width);
            }
        }
        return rows.build();
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private final class Builder {
        private final List<Slot> slots = new ArrayList<>();
        private final List<Group> groups = new ArrayList<>();
        private int cursor = CrystalScreenLayout.this.content.y() + CrystalScreenLayout.this.firstRowInset;
        private int row = -1;
        private int rowY;
        private int rowHeight;
        private int textY = -1;
        private String groupKey;
        private int groupTop;
        private int groupBottom;
        private int groupFirstRow;
        private boolean groupStartsNext;

        void group(String key) {
            closeGroup();
            this.groupKey = key;
            this.groupStartsNext = true;
        }

        Builder row(int height) {
            if (this.row >= 0) {
                this.cursor += this.groupStartsNext ? CrystalScreenLayout.this.groupGap : CrystalScreenLayout.this.rowGap;
            }
            this.row++;
            this.rowY = this.cursor;
            this.rowHeight = Math.max(1, height);
            this.cursor += this.rowHeight;
            if (this.groupStartsNext) {
                this.groupTop = this.rowY;
                this.groupFirstRow = this.row;
                this.groupStartsNext = false;
            }
            this.groupBottom = this.cursor;
            return this;
        }

        Builder add(String name, int x, int width) {
            this.slots.add(new Slot(name, new Rect(x, this.rowY, Math.max(1, width), this.rowHeight),
                    this.row, this.groups.size()));
            return this;
        }

        /** A line of plain text under the last row, inside the same card. */
        void textLine(int height) {
            this.textY = this.cursor + 2;
            this.cursor = this.textY + height;
            this.groupBottom = this.cursor;
        }

        private void closeGroup() {
            if (this.groupKey == null || this.groupStartsNext) {
                return;
            }
            int padding = CrystalScreenLayout.this.cardPadding;
            this.groups.add(new Group(this.groupKey,
                    new Rect(CrystalScreenLayout.this.optionsX - 4, this.groupTop - padding,
                            CrystalScreenLayout.this.optionsWidth + 8,
                            this.groupBottom - this.groupTop + padding * 2),
                    this.groupFirstRow));
            this.groupKey = null;
        }

        Rows build() {
            closeGroup();
            int bottom = this.cursor + CrystalScreenLayout.this.cardPadding;
            return new Rows(List.copyOf(this.slots), List.copyOf(this.groups),
                    bottom - CrystalScreenLayout.this.content.y(), this.textY);
        }
    }
}
