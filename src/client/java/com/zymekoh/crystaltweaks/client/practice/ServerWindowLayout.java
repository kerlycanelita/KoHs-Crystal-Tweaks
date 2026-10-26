package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;

/**
 * Where the window showing the parked server goes, over the practice world: the top-left corner,
 * which Minecraft's HUD leaves empty, kept clear of the chat in the bottom-left corner and of the
 * boss bars in the middle. It touches no Minecraft class, so tools/tests/ServerWindowLayoutTest
 * checks it across thousands of window sizes.
 *
 * <pre>
 *  +---------------------------+
 *  | (o) Server name     42 ms |  header
 *  | +-----------------------+ |
 *  | |  the server's world   | |  live view, the game window's shape
 *  | +-----------------------+ |
 *  | alert, title, action bar  |  body: as many lines as fit
 *  | boss bars, sidebar, chat  |
 *  +---------------------------+
 * </pre>
 */
public final class ServerWindowLayout {
    public static final int MIN_WIDTH = 120;
    public static final int MAX_WIDTH = 230;
    /** Height of one line of text in the body. */
    public static final int LINE = 10;
    public static final int HEADER = 13;
    public static final int PADDING = 4;
    /** Minecraft's chat, unfocused, at its default height, plus the hotbar under it. */
    public static final int CHAT_RESERVE = 40 + 90;
    /** Half the width of a boss bar, which sits in the middle of the top edge. */
    public static final int BOSS_BAR_HALF = 92;
    public static final int MIN_VIEW_HEIGHT = 36;

    public final int screenWidth;
    public final int screenHeight;
    public final int margin;
    /** The whole window at its tallest; what it shows decides how much of it is used. */
    public final Rect card;
    public final Rect header;
    /** The live view, or empty when there is none or no room for it. */
    public final Rect view;
    /** The lines under the view. */
    public final Rect body;
    public final int bodyLines;

    private ServerWindowLayout(int width, int height, boolean withView, float viewAspect) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.margin = Math.max(2, Math.min(6, Math.min(width, height) / 80));
        int widest = Math.min(MAX_WIDTH, width / 2 - BOSS_BAR_HALF - this.margin * 2);
        int cardWidth = Math.max(Math.min(MIN_WIDTH, width - this.margin * 2), Math.min(widest, Math.round(width * 0.30F)));
        cardWidth = Math.max(1, Math.min(cardWidth, width - this.margin * 2));
        int bottom = Math.max(this.margin + HEADER + PADDING * 2, height - CHAT_RESERVE - this.margin);
        bottom = Math.min(bottom, height - this.margin);
        int cardHeight = Math.max(1, bottom - this.margin);
        this.card = new Rect(this.margin, this.margin, cardWidth, cardHeight);
        int innerX = this.margin + PADDING;
        int innerWidth = Math.max(1, cardWidth - PADDING * 2);
        this.header = new Rect(innerX, this.margin + 2, innerWidth, HEADER);

        int top = this.header.bottom() + 2;
        int room = this.card.bottom() - PADDING - top;
        Rect shown = Rect.EMPTY;
        if (withView && viewAspect > 0.1F) {
            int viewWidth = innerWidth;
            int viewHeight = Math.round(viewWidth / viewAspect);
            // The view takes at most two thirds of the room, so a few lines always fit under it.
            int most = room * 2 / 3;
            if (viewHeight > most) {
                viewHeight = most;
                viewWidth = Math.min(innerWidth, Math.round(viewHeight * viewAspect));
            }
            if (viewHeight >= MIN_VIEW_HEIGHT) {
                shown = new Rect(innerX + (innerWidth - viewWidth) / 2, top, viewWidth, viewHeight);
                top = shown.bottom() + 3;
            }
        }
        this.view = shown;
        int bodyHeight = Math.max(0, this.card.bottom() - PADDING - top);
        this.body = new Rect(innerX, top, innerWidth, bodyHeight);
        this.bodyLines = bodyHeight / LINE;
    }

    public static ServerWindowLayout fit(int width, int height, boolean withView, float viewAspect) {
        return new ServerWindowLayout(Math.max(1, width), Math.max(1, height), withView, viewAspect);
    }
}
