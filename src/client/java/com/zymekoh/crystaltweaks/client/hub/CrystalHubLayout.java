package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;

/**
 * The geometry of the Crystal Tweaks settings, the grimoire, computed once per window size and
 * shared by drawing and input. It touches no Minecraft class, so tools/tests/CrystalHubLayoutTest
 * checks it across thousands of window sizes without starting the game.
 *
 * <pre>
 *  ┌──────────────┬┬───────────────┬┬──────────────────────────┐
 *  │ Crystal      ││               ││ Brillo                ↺  │
 *  │ Tweaks       ││     (the      ││ the section's rows, that  │
 *  │ [Ajenos]     ││    crystal    ││ scroll                    │
 *  │  Colores     ││   on its      ││                           │
 *  │  Brillo  ——  ││   sigil)      ││                           │
 *  │  …           ││   Aurora      ││                           │
 *  │ [Práct][KoHs]││ [Probar dest.]││ v2.7.0            [Listo] │
 *  └──────────────┴┴───────────────┴┴──────────────────────────┘
 *    sections       the preview       the open section
 * </pre>
 *
 * <p>Three pages of one book: the sections on the left, listed by name at every usual size of the
 * GUI; the crystal in the middle; the open section's rows on the right. A shorter window takes the
 * title and the corner buttons up into a header over the book; a narrower one leaves the crystal
 * out before it gives up the sections' names; only the narrowest folds the sections into a strip
 * of icons. The KoHs page is a window of its own over the whole book, with its own way back.
 * 240x140 is the smallest supported.</p>
 */
public final class CrystalHubLayout {
    public static final int MAX_WIDTH = 880;
    public static final int MAX_HEIGHT = 440;
    public static final int MIN_WIDTH = 240;
    public static final int MIN_HEIGHT = 140;
    public static final int NAV_MIN = 104;
    public static final int NAV_MAX = 210;
    public static final int CENTER_MIN = 96;
    public static final int CENTER_MAX = 290;
    /** The open section's page never gets narrower than this while anything else is on screen. */
    public static final int PAGE_MIN = 176;
    /** Room the page keeps before the crystal takes any more of the book. */
    public static final int PAGE_EASY = 230;

    /** The sections' list, the crystal and the page; the list and the page; a strip of icons and the page. */
    public enum Mode { TRIPTYCH, DIPTYCH, NARROW }

    public final int screenWidth;
    public final int screenHeight;
    public final Mode mode;
    public final boolean compact;
    public final int margin;
    /** Over the book when the window is short or narrow: the title and the corner buttons. Empty otherwise. */
    public final Rect header;
    /** The book's cover, and inside it the pages. */
    public final Rect book;
    public final Rect body;
    public final Rect enemySwitch;
    public final Rect kohsButton;
    public final Rect title;
    /** The sections' page, and the list or strip of sections on it. */
    public final Rect leftWing;
    public final Rect leftRail;
    /** The open section's rows, on the right page. */
    public final Rect leftPanel;
    /** The right page itself. */
    public final Rect rightWing;
    /** Unused by the grimoire: one page shows one section. */
    public final Rect rightRail;
    public final Rect rightPanel;
    /** The middle page: the crystal on its sigil, what it wears, and how to click it. Empty without room for it. */
    public final Rect center;
    public final Rect stage;
    public final Rect caption;
    /** At the foot of the middle page: the two mice that say left click explodes the crystal and right click places it. */
    public final Rect hint;
    /** Crystal Practice's way in, at the foot of the sections' page; empty in NARROW, where it is a section of its own. */
    public final Rect practice;
    /** Under the right page's rows: the version, and Done. */
    public final Rect footer;
    public final Rect done;
    /** The KoHs page, a window of its own over the whole book, and its buttons back and out under it. */
    public final Rect kohsArea;
    public final Rect kohsBack;
    public final Rect kohsDone;
    public final int control;
    public final int gap;
    public final int panelHeader;
    public final int panelPadding;
    public final int railWidth;
    /** The gap between two pages, where the spine shows. */
    public final int spine;
    /** How tall a section is in the list on the sections' page. */
    public final int listRowHeight;

    private CrystalHubLayout(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.compact = height < 260 || width < 420;
        this.margin = clamp(Math.min(width, height) / 30, 3, 12);
        this.control = this.compact ? 15 : 18;
        this.gap = this.compact ? 2 : 3;
        // A page's head: its gothic title, and under it a line on what the section is.
        this.panelHeader = this.compact ? 18 : 40;
        this.panelPadding = this.compact ? 5 : 8;
        this.railWidth = this.compact ? 18 : 22;
        this.spine = this.compact ? 3 : 5;
        this.listRowHeight = this.compact ? 14 : 18;
        int frameWidth = Math.max(1, Math.min(MAX_WIDTH, width - this.margin * 2));
        int frameHeight = Math.max(1, Math.min(MAX_HEIGHT, height - this.margin * 2));
        int frameX = (width - frameWidth) / 2;
        int frameY = (height - frameHeight) / 2;
        int cover = this.compact ? 2 : 4;
        int innerWidth = frameWidth - cover * 2;

        // A list of names needs nine readable rows and Practice under them: below that height, the strip.
        boolean listFits = frameHeight >= 161;
        Mode chosen;
        if (listFits && innerWidth >= NAV_MIN + CENTER_MIN + PAGE_MIN + this.spine * 2) {
            chosen = Mode.TRIPTYCH;
        } else if (listFits && innerWidth >= NAV_MIN + PAGE_MIN + this.spine) {
            chosen = Mode.DIPTYCH;
        } else {
            chosen = Mode.NARROW;
        }
        this.mode = chosen;
        // The title and the switch stand at the head of the sections' page when it is tall enough
        // for them, nine sections and Practice; otherwise they go up into a header over the book.
        boolean headed = chosen == Mode.NARROW || frameHeight < 200;

        int headerHeight = headed ? (this.compact ? 16 : 20) : 0;
        this.header = headed ? new Rect(frameX, frameY, frameWidth, headerHeight) : Rect.EMPTY;
        int bookTop = frameY + (headed ? headerHeight + 3 : 0);
        this.book = new Rect(frameX, bookTop, frameWidth, Math.max(1, frameY + frameHeight - bookTop));
        this.body = new Rect(this.book.x() + cover, this.book.y() + cover, Math.max(1, this.book.width() - cover * 2),
                Math.max(1, this.book.height() - cover * 2));

        int pad = this.compact ? 5 : 8;
        int buttonHeight = this.control;
        if (chosen == Mode.NARROW) {
            int strip = this.railWidth + 8;
            this.leftWing = new Rect(this.body.x(), this.body.y(), strip, this.body.height());
            this.leftRail = new Rect(this.leftWing.x() + 4, this.leftWing.y() + 4, this.railWidth, Math.max(1, this.leftWing.height() - 8));
            this.center = Rect.EMPTY;
            this.rightWing = new Rect(this.leftWing.right() + this.spine, this.body.y(),
                    Math.max(1, this.body.right() - this.leftWing.right() - this.spine), this.body.height());
            this.practice = Rect.EMPTY;
        } else {
            int nav = clamp(Math.round(this.body.width() * 0.25F), NAV_MIN, NAV_MAX);
            int middle = 0;
            if (chosen == Mode.TRIPTYCH) {
                middle = clamp(Math.round(this.body.width() * 0.27F), CENTER_MIN, CENTER_MAX);
                // The page keeps its room first, then the crystal, then the sections' page gives way.
                int over = nav + middle + this.spine * 2 + PAGE_MIN - this.body.width();
                if (over > 0) {
                    int fromMiddle = Math.min(over, middle - CENTER_MIN);
                    middle -= fromMiddle;
                    nav = Math.max(NAV_MIN, nav - (over - fromMiddle));
                }
                // The crystal takes up to a twentieth more of the book from the page, while the page keeps its ease.
                int spare = this.body.width() - nav - middle - this.spine * 2 - PAGE_EASY;
                if (spare > 0) {
                    middle = Math.min(CENTER_MAX, middle + Math.min(spare, Math.round(this.body.width() * 0.05F)));
                }
            } else if (nav + this.spine + PAGE_MIN > this.body.width()) {
                nav = Math.max(NAV_MIN, this.body.width() - this.spine - PAGE_MIN);
            }
            this.leftWing = new Rect(this.body.x(), this.body.y(), nav, this.body.height());
            this.center = chosen == Mode.TRIPTYCH ? new Rect(this.leftWing.right() + this.spine, this.body.y(), middle, this.body.height())
                    : Rect.EMPTY;
            int pageX = (this.center.isEmpty() ? this.leftWing.right() : this.center.right()) + this.spine;
            this.rightWing = new Rect(pageX, this.body.y(), Math.max(1, this.body.right() - pageX), this.body.height());
            // The foot of the sections' page: Practice, and KoHs beside it when the title is there too.
            int inner = this.leftWing.width() - pad * 2;
            int bottom = this.leftWing.bottom() - pad - buttonHeight;
            int listTop;
            if (headed) {
                this.practice = new Rect(this.leftWing.x() + pad, bottom, inner, buttonHeight);
                listTop = this.leftWing.y() + pad;
            } else {
                int titleHeight = this.compact ? 18 : 24;
                Rect titleRect = new Rect(this.leftWing.x() + pad, this.leftWing.y() + pad, inner, titleHeight);
                Rect switchRect = new Rect(titleRect.x(), titleRect.bottom() + 4, inner, buttonHeight);
                int half = (inner - 4) / 2;
                this.practice = new Rect(titleRect.x(), bottom, half, buttonHeight);
                listTop = switchRect.bottom() + (this.compact ? 6 : 10);
            }
            this.leftRail = new Rect(this.leftWing.x() + pad, listTop, inner, Math.max(1, bottom - 6 - listTop));
        }
        if (headed) {
            // The header: the switch on the left, KoHs on the right, the title between them.
            int buttonTop = this.header.y() + (headerHeight - buttonHeight) / 2;
            int switchWidth = Math.min(frameWidth / 3, clamp(frameWidth / 4, 60, 140));
            this.enemySwitch = new Rect(frameX, buttonTop, switchWidth, buttonHeight);
            int kohsWidth = Math.min(frameWidth / 4, this.compact ? 44 : 60);
            this.kohsButton = new Rect(this.header.right() - kohsWidth, buttonTop, kohsWidth, buttonHeight);
            this.title = new Rect(this.enemySwitch.right() + 4, this.header.y(),
                    Math.max(1, this.kohsButton.x() - 4 - this.enemySwitch.right() - 4), headerHeight);
        } else {
            int inner = this.leftWing.width() - pad * 2;
            int titleHeight = this.compact ? 18 : 24;
            this.title = new Rect(this.leftWing.x() + pad, this.leftWing.y() + pad, inner, titleHeight);
            this.enemySwitch = new Rect(this.title.x(), this.title.bottom() + 4, inner, buttonHeight);
            int half = (inner - 4) / 2;
            this.kohsButton = new Rect(this.practice.right() + 4, this.practice.y(), inner - half - 4, buttonHeight);
        }
        this.rightRail = Rect.EMPTY;
        this.rightPanel = Rect.EMPTY;

        // The right page: the open section's rows over a row with the version and Done.
        int doneWidth = Math.min(this.rightWing.width() / 3, this.compact ? 56 : 72);
        int footerHeight = buttonHeight;
        int footerY = this.rightWing.bottom() - pad - footerHeight;
        this.footer = new Rect(this.rightWing.x() + pad, footerY, Math.max(1, this.rightWing.width() - pad * 2), footerHeight);
        this.done = new Rect(this.footer.right() - doneWidth, footerY, doneWidth, footerHeight);
        this.leftPanel = new Rect(this.rightWing.x() + pad, this.rightWing.y() + pad, Math.max(1, this.rightWing.width() - pad * 2),
                Math.max(1, footerY - 4 - this.rightWing.y() - pad));

        // The middle page: the crystal, what it wears under it, and at its foot how to click it.
        if (this.center.isEmpty()) {
            this.stage = Rect.EMPTY;
            this.caption = Rect.EMPTY;
            this.hint = Rect.EMPTY;
        } else {
            int pageWidth = Math.max(1, this.center.width() - pad * 2);
            int hintHeight = this.compact ? 11 : 13;
            this.hint = new Rect(this.center.x() + pad, this.center.bottom() - pad - hintHeight, pageWidth, hintHeight);
            int captionHeight = Math.min(this.compact ? 26 : 40, Math.max(0, (this.center.height() - pad * 2 - hintHeight) / 3));
            // The crystal comes first: what it wears is left out when it would leave the crystal too little room.
            int room = this.hint.y() - this.center.y() - pad;
            this.caption = captionHeight < 18 || room - captionHeight - 6 < 90 ? Rect.EMPTY
                    : new Rect(this.center.x() + pad, this.hint.y() - 4 - captionHeight, pageWidth, captionHeight);
            int stageBottom = (this.caption.isEmpty() ? this.hint.y() : this.caption.y()) - 2;
            this.stage = new Rect(this.center.x() + pad, this.center.y() + pad, pageWidth, Math.max(1, stageBottom - this.center.y() - pad));
        }

        // The KoHs window: the whole book, the page above, Back and Done in a row under it.
        int kohsButtonWidth = Math.min(this.body.width() / 3, this.compact ? 64 : 90);
        int kohsRowY = this.body.bottom() - pad - buttonHeight;
        this.kohsBack = new Rect(this.body.x() + pad, kohsRowY, kohsButtonWidth, buttonHeight);
        this.kohsDone = new Rect(this.body.right() - pad - kohsButtonWidth, kohsRowY, kohsButtonWidth, buttonHeight);
        this.kohsArea = new Rect(this.body.x() + pad, this.body.y() + pad, Math.max(1, this.body.width() - pad * 2),
                Math.max(1, kohsRowY - 4 - this.body.y() - pad));
    }

    public static CrystalHubLayout fit(int width, int height) {
        return new CrystalHubLayout(Math.max(1, width), Math.max(1, height));
    }

    /** Whether the sections show as a list with their names, rather than a strip of icons. */
    public boolean sectionList() {
        return this.mode != Mode.NARROW;
    }

    /** The part of the right page its rows scroll in, below its title; the scrollbar sits right of it. */
    public Rect panelViewport(Rect panel) {
        if (panel.isEmpty()) {
            return Rect.EMPTY;
        }
        return new Rect(panel.x(), panel.y() + this.panelHeader + 2, Math.max(1, panel.width() - 5),
                Math.max(1, panel.height() - this.panelHeader - 2));
    }

    /** The title of the right page: the open section's name. */
    public Rect panelTitle(Rect panel) {
        return panel.isEmpty() ? Rect.EMPTY : new Rect(panel.x(), panel.y(), panel.width(), this.panelHeader);
    }

    /** The {@code index}-th of {@code count} sections in a list on the sections' page, from the top. */
    public static Rect listRow(Rect list, int index, int count, int rowHeight) {
        if (list.isEmpty() || count <= 0) {
            return Rect.EMPTY;
        }
        int height = Math.max(8, Math.min(rowHeight, list.height() / count));
        return new Rect(list.x(), list.y() + index * height, list.width(), height);
    }

    /** The {@code index}-th of {@code count} section buttons on a strip, from the top, shrinking to fit. */
    public static Rect railButton(Rect rail, int index, int count) {
        if (rail.isEmpty() || count <= 0) {
            return Rect.EMPTY;
        }
        int spacing = 3;
        int size = Math.max(6, Math.min(rail.width(), (rail.height() - 2 - spacing * (count - 1)) / count));
        return new Rect(rail.x() + (rail.width() - size) / 2, rail.y() + 2 + index * (size + spacing), size, size);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
