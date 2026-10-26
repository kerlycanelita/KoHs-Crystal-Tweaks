package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;

/**
 * The geometry of the Crystal Practice window, computed once per window size and shared by drawing
 * and input. It has two forms: the window over the menus, with a preview beside the options when
 * there is room, and the pause panel on the right of the screen inside a practice world, the paused
 * world left in view. It touches no Minecraft class, so tools/tests/PracticeLayoutTest checks it
 * across thousands of sizes without starting the game.
 *
 * <pre>
 *  +-------------------------------------------+
 *  | (crystal) CRYSTAL PRACTICE   EXPERIMENTAL |  header
 *  | [ Gear ]  [ Bot ]  [ World ]              |  tabs
 *  | options, scrolling   |  preview           |  body
 *  |                      |                    |
 *  | * status (in a world only)                |
 *  | [ Back ]           [ Enter practice ]     |  footer (two rows in a world)
 *  +-------------------------------------------+
 * </pre>
 */
public final class PracticeLayout {
    public static final int MAX_WIDTH = 520;
    public static final int MAX_HEIGHT = 340;
    public static final int SIDE_MIN = 190;
    public static final int SIDE_MAX = 300;

    public final int screenWidth;
    public final int screenHeight;
    public final boolean inWorld;
    public final boolean compact;
    public final boolean preview;
    public final int margin;
    public final int padding;
    /** Height of a row of controls in the option list. */
    public final int row;
    public final int gap;
    public final Rect panel;
    public final Rect header;
    public final Rect tabs;
    public final Rect[] tab = new Rect[3];
    /** The option list's viewport: the scrollbar sits just right of it, inside the panel. */
    public final Rect content;
    public final Rect previewArea;
    /** The line telling whether a server is connected behind, in a world; empty otherwise. */
    public final Rect status;
    public final Rect footer;
    /** Back and Enter practice; in a world, Back to game, Options and the way out. */
    public final Rect[] buttons;

    private PracticeLayout(int width, int height, boolean inWorld) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.inWorld = inWorld;
        this.margin = clamp(Math.min(width, height) / 24, 4, 16);
        int availableWidth = Math.max(1, width - this.margin * 2);
        int availableHeight = Math.max(1, height - this.margin * 2);
        int panelWidth;
        int panelHeight;
        int panelX;
        int panelY;
        if (inWorld) {
            panelWidth = Math.min(availableWidth, clamp(width * 38 / 100, SIDE_MIN, SIDE_MAX));
            panelHeight = availableHeight;
            panelX = width - this.margin - panelWidth;
            panelY = this.margin;
        } else {
            panelWidth = Math.min(availableWidth, MAX_WIDTH);
            panelHeight = Math.min(availableHeight, MAX_HEIGHT);
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
        }
        this.panel = new Rect(panelX, panelY, panelWidth, panelHeight);
        this.compact = panelHeight < 230 || panelWidth < 250;
        this.padding = this.compact ? 6 : 9;
        this.row = this.compact ? 14 : 16;
        this.gap = this.compact ? 2 : 3;
        int headerHeight = this.compact ? 16 : 21;
        int tabsHeight = this.compact ? 15 : 18;
        int buttonHeight = this.compact ? 16 : 18;
        int innerX = panelX + this.padding;
        int innerWidth = Math.max(1, panelWidth - this.padding * 2);

        this.header = new Rect(innerX, panelY + 3, innerWidth, headerHeight);
        this.tabs = new Rect(innerX, this.header.bottom() + 2, innerWidth, tabsHeight);
        int tabGap = 3;
        int tabWidth = Math.max(1, (innerWidth - tabGap * 2) / 3);
        for (int index = 0; index < 3; index++) {
            int x = innerX + (tabWidth + tabGap) * index;
            int w = index == 2 ? innerX + innerWidth - x : tabWidth;
            this.tab[index] = new Rect(x, this.tabs.y(), w, tabsHeight);
        }

        int footerHeight = inWorld ? buttonHeight * 2 + 3 : buttonHeight;
        int bottom = panelY + panelHeight - this.padding + 2;
        this.footer = new Rect(innerX, bottom - footerHeight, innerWidth, footerHeight);
        if (inWorld) {
            int resume = Math.max(1, (innerWidth - 4) * 3 / 5);
            this.buttons = new Rect[] {
                    new Rect(innerX, this.footer.y(), resume, buttonHeight),
                    new Rect(innerX + resume + 4, this.footer.y(), innerWidth - resume - 4, buttonHeight),
                    new Rect(innerX, this.footer.y() + buttonHeight + 3, innerWidth, buttonHeight)};
            this.status = new Rect(innerX, this.footer.y() - 13, innerWidth, 10);
        } else {
            int back = Math.max(1, (innerWidth - 6) * 2 / 5);
            this.buttons = new Rect[] {
                    new Rect(innerX, this.footer.y(), back, buttonHeight),
                    new Rect(innerX + back + 6, this.footer.y(), innerWidth - back - 6, buttonHeight)};
            this.status = Rect.EMPTY;
        }

        int bodyTop = this.tabs.bottom() + (this.compact ? 4 : 7);
        int bodyBottom = (this.status.isEmpty() ? this.footer.y() : this.status.y()) - (this.compact ? 3 : 6);
        int bodyHeight = Math.max(1, bodyBottom - bodyTop);
        // Room for the scrollbar right of the list, inside the panel.
        int listRoom = innerWidth - 5;
        this.preview = !inWorld && panelWidth >= 380 && bodyHeight >= 130;
        if (this.preview) {
            int previewWidth = clamp(innerWidth * 2 / 5, 150, 220);
            int listWidth = listRoom - previewWidth - 8;
            this.content = new Rect(innerX, bodyTop, Math.max(1, listWidth), bodyHeight);
            this.previewArea = new Rect(innerX + innerWidth - previewWidth, bodyTop, previewWidth, bodyHeight);
        } else {
            this.content = new Rect(innerX, bodyTop, Math.max(1, listRoom), bodyHeight);
            this.previewArea = Rect.EMPTY;
        }
    }

    public static PracticeLayout fit(int width, int height, boolean inWorld) {
        return new PracticeLayout(Math.max(1, width), Math.max(1, height), inWorld);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
