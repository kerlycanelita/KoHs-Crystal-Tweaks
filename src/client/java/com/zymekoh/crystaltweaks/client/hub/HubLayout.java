package com.zymekoh.crystaltweaks.client.hub;

/**
 * The geometry of the settings hub, computed once per window size and shared by drawing and input.
 * It touches no Minecraft class, so tools/tests/HubLayoutTest checks it across thousands of sizes
 * without starting the game.
 *
 * <pre>
 *  [switch]          CRYSTAL TWEAKS KoHs          [status]     header
 *  +--------+  [ Sound | Crystal Tweaks | KoHs ]  +--------+
 *  | Colors |                                     |  Glow  |
 *  | panel  |          the crystal (stage)        | panel  |  body
 *  |        |                                     |        |
 *  +--------+       [ Crystal Practice ]          +--------+
 *  v2.x                                             [Done]    footer
 * </pre>
 *
 * <p>Wide windows get the three columns. Narrower ones keep the crystal in the middle and turn the
 * two panels into drawers that slide over it from handles at its edges, one at a time, so neither
 * the controls nor the crystal ever shrink to nothing.</p>
 */
public final class HubLayout {
    public static final int MAX_BODY_WIDTH = 1000;
    public static final int MAX_BODY_HEIGHT = 600;
    public static final int SIDE_MIN = 128;
    public static final int SIDE_MAX = 236;
    public static final int CENTER_MIN = 150;
    public static final int CENTER_MAX = 520;

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public int centerX() {
            return this.x + this.width / 2;
        }

        public int centerY() {
            return this.y + this.height / 2;
        }

        public boolean isEmpty() {
            return this.width <= 0 || this.height <= 0;
        }

        public boolean contains(double px, double py) {
            return !isEmpty() && px >= this.x && px < right() && py >= this.y && py < bottom();
        }

        public boolean inside(Rect outer) {
            return this.x >= outer.x && this.y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
        }

        public boolean overlaps(Rect other) {
            return !isEmpty() && !other.isEmpty() && this.x < other.right() && other.x < right()
                    && this.y < other.bottom() && other.y < bottom();
        }

        public Rect inset(int amount) {
            return new Rect(this.x + amount, this.y + amount, Math.max(0, this.width - amount * 2),
                    Math.max(0, this.height - amount * 2));
        }

        public Rect withHeight(int newHeight) {
            return new Rect(this.x, this.y, this.width, Math.max(0, newHeight));
        }
    }

    public final int screenWidth;
    public final int screenHeight;
    /** Three columns; otherwise the side panels are drawers over the centre. */
    public final boolean wide;
    /** Smaller header, rows and gaps, for windows short on height. */
    public final boolean compact;
    public final int margin;
    public final Rect header;
    public final Rect switchButton;
    public final Rect footer;
    public final Rect done;
    public final Rect body;
    public final Rect left;
    public final Rect right;
    public final Rect center;
    public final Rect carousel;
    public final Rect stage;
    public final Rect practice;
    /** The handles that open the side drawers on a narrow window; empty when wide. */
    public final Rect leftHandle;
    public final Rect rightHandle;
    public final int controlHeight;
    public final int sliderHeight;
    public final int rowGap;
    public final int panelHeader;
    public final int panelPadding;

    private HubLayout(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);
        this.compact = this.screenHeight < 300 || this.screenWidth < 480;
        this.margin = clamp(this.screenWidth / 48, 4, 14);
        int headerHeight = this.compact ? 17 : 21;
        int footerHeight = this.compact ? 17 : 21;
        this.controlHeight = this.compact ? 15 : 16;
        this.sliderHeight = this.compact ? 14 : 15;
        this.rowGap = this.compact ? 3 : 4;
        this.panelHeader = this.compact ? 15 : 18;
        this.panelPadding = this.compact ? 4 : 5;

        int availableWidth = this.screenWidth - this.margin * 2;
        int availableHeight = this.screenHeight - this.margin * 2 - headerHeight - footerHeight - 4;
        int bodyWidth = Math.max(1, Math.min(MAX_BODY_WIDTH, availableWidth));
        int bodyHeight = Math.max(1, Math.min(MAX_BODY_HEIGHT, availableHeight));
        int frameHeight = headerHeight + 2 + bodyHeight + 2 + footerHeight;
        int top = Math.max(this.margin, (this.screenHeight - frameHeight) / 2);
        int bodyX = (this.screenWidth - bodyWidth) / 2;
        this.header = new Rect(bodyX, top, bodyWidth, headerHeight);
        this.body = new Rect(bodyX, this.header.bottom() + 2, bodyWidth, bodyHeight);
        this.footer = new Rect(bodyX, this.body.bottom() + 2, bodyWidth, footerHeight);

        int switchWidth = clamp(bodyWidth / 4, 70, 150);
        this.switchButton = new Rect(bodyX, top + (headerHeight - this.controlHeight) / 2, switchWidth, this.controlHeight);
        int doneWidth = this.compact ? 56 : 72;
        int doneHeight = this.compact ? 14 : 16;
        this.done = new Rect(this.footer.right() - doneWidth, this.footer.y() + (footerHeight - doneHeight) / 2, doneWidth,
                doneHeight);

        int gap = clamp(bodyWidth / 64, 4, 10);
        boolean roomForThree = bodyWidth >= SIDE_MIN * 2 + CENTER_MIN + gap * 2 && bodyHeight >= 120;
        this.wide = roomForThree;
        if (roomForThree) {
            int side = clamp(Math.round(bodyWidth * 0.27F), SIDE_MIN, SIDE_MAX);
            int centerWidth = bodyWidth - side * 2 - gap * 2;
            if (centerWidth > CENTER_MAX) {
                // Wider than that, the crystal only floats in more empty space: the panels get it.
                side = Math.min(SIDE_MAX + 40, (bodyWidth - CENTER_MAX - gap * 2) / 2);
                centerWidth = bodyWidth - side * 2 - gap * 2;
            }
            this.left = new Rect(bodyX, this.body.y(), side, bodyHeight);
            this.right = new Rect(this.body.right() - side, this.body.y(), side, bodyHeight);
            this.center = new Rect(this.left.right() + gap, this.body.y(), centerWidth, bodyHeight);
            this.leftHandle = Rect.EMPTY;
            this.rightHandle = Rect.EMPTY;
        } else {
            int drawer = Math.max(1, Math.min(SIDE_MAX, bodyWidth - 26));
            this.left = new Rect(bodyX, this.body.y(), drawer, bodyHeight);
            this.right = new Rect(this.body.right() - drawer, this.body.y(), drawer, bodyHeight);
            int handleHeight = Math.min(56, Math.max(24, bodyHeight / 3));
            int handleY = this.body.y() + (bodyHeight - handleHeight) / 2;
            this.leftHandle = new Rect(bodyX, handleY, 11, handleHeight);
            this.rightHandle = new Rect(this.body.right() - 11, handleY, 11, handleHeight);
            this.center = new Rect(bodyX + 13, this.body.y(), Math.max(1, bodyWidth - 26), bodyHeight);
        }

        int carouselHeight = this.compact ? 17 : 20;
        this.carousel = new Rect(this.center.x(), this.center.y(), this.center.width(), carouselHeight);
        int practiceHeight = this.compact ? 16 : 19;
        int practiceWidth = Math.min(this.center.width(), clamp(Math.round(this.center.width() * 0.66F), 96, 214));
        this.practice = new Rect(this.center.centerX() - practiceWidth / 2, this.center.bottom() - practiceHeight,
                practiceWidth, practiceHeight);
        int stageTop = this.carousel.bottom() + 3;
        this.stage = new Rect(this.center.x(), stageTop, this.center.width(), Math.max(1, this.practice.y() - 3 - stageTop));
    }

    public static HubLayout fit(int width, int height) {
        return new HubLayout(width, height);
    }

    /** The part of a side panel its rows scroll in, below its title. */
    public Rect panelViewport(Rect panel) {
        int scrollbar = 4;
        return new Rect(panel.x() + this.panelPadding, panel.y() + this.panelHeader + 2,
                Math.max(1, panel.width() - this.panelPadding * 2 - scrollbar),
                Math.max(1, panel.height() - this.panelHeader - 2 - this.panelPadding));
    }

    /** The title strip of a side panel. */
    public Rect panelTitle(Rect panel) {
        return new Rect(panel.x(), panel.y(), panel.width(), this.panelHeader);
    }

    /** The reset button in a side panel's title strip. */
    public Rect panelReset(Rect panel) {
        int size = this.panelHeader - 6;
        return new Rect(panel.right() - size - 4, panel.y() + 3, size, size);
    }

    /**
     * Where a drawer of {@code wanted} pixels sits: under the carousel, at most {@code share} of
     * the stage, so the crystal below keeps the rest.
     */
    public Rect drawer(int wanted, float share) {
        int height = Math.max(0, Math.min(wanted, Math.round(this.stage.height() * share)));
        int width = Math.min(this.stage.width(), Math.max(SIDE_MIN, Math.round(this.stage.width() * 0.96F)));
        return new Rect(this.stage.centerX() - width / 2, this.stage.y(), width, height);
    }

    /** The crystal's square under an open drawer of {@code drawerHeight} pixels. */
    public Rect crystal(int drawerHeight) {
        int top = this.stage.y() + (drawerHeight > 0 ? drawerHeight + 3 : 0);
        int height = this.stage.bottom() - top;
        int size = Math.max(0, Math.min(this.stage.width() - 8, height));
        return new Rect(this.stage.centerX() - size / 2, top + (height - size) / 2, size, size);
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
