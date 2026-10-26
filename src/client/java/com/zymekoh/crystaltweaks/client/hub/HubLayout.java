package com.zymekoh.crystaltweaks.client.hub;

/**
 * The rectangle the hub's layouts are built from. It touches no Minecraft class, so the layout
 * tests run without starting the game.
 */
public final class HubLayout {
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

        public Rect offset(int dx, int dy) {
            return new Rect(this.x + dx, this.y + dy, this.width, this.height);
        }

        public Rect withHeight(int newHeight) {
            return new Rect(this.x, this.y, this.width, Math.max(0, newHeight));
        }
    }

    private HubLayout() {
    }
}
