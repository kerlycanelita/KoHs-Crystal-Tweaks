package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The change between your crystals and the enemy's: the menu on screen breaks apart and the other
 * one falls into its place.
 *
 * <p>Cracks run out from the switch first, white-hot. Then every panel splits into shards that fly
 * away from the impact, turning and falling, while the crystal in the middle explodes. Last, the new
 * menu's panels drop from above one after another, land heavily with a burst of dust and a flash
 * along their edge, and only then do their controls come up. No widget moves during any of it: the
 * old controls are gone before the first crack and the new ones appear once everything has landed,
 * so nothing can be clicked where it is not drawn.</p>
 */
final class ModeTransition {
    static final long CRACK_NANOS = 170_000_000L;
    static final long SHATTER_NANOS = 820_000_000L;
    static final long DROP_START_NANOS = 560_000_000L;
    static final long DROP_NANOS = 520_000_000L;
    static final long DROP_STAGGER_NANOS = 60_000_000L;
    static final long SETTLE_NANOS = 260_000_000L;

    private record Piece(float x, float y, float width, float height, float velocityX, float velocityY, float spin,
            int color, int edge) {
    }

    private record Crack(float[] xs, float[] ys) {
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final List<Crack> cracks = new ArrayList<>();
    private final List<Rect> incoming;
    private final long startedAt;
    private final int impactX;
    private final int impactY;
    private final int oldHalo;
    private final int newTop;
    private final int newBottom;
    private final int newBorder;
    private final int newAccent;

    ModeTransition(long now, List<Rect> outgoing, int impactX, int impactY, int oldTop, int oldBottom, int oldBorder,
            int oldHalo, List<Rect> incoming, int newTop, int newBottom, int newBorder, int newAccent, int screenWidth,
            int screenHeight) {
        this.startedAt = now;
        this.impactX = impactX;
        this.impactY = impactY;
        this.oldHalo = oldHalo;
        this.incoming = incoming;
        this.newTop = newTop;
        this.newBottom = newBottom;
        this.newBorder = newBorder;
        this.newAccent = newAccent;
        int seed = 0;
        for (Rect rect : outgoing) {
            if (rect.isEmpty()) {
                continue;
            }
            int columns = Math.max(2, Math.min(9, rect.width() / 24));
            int rows = Math.max(2, Math.min(12, rect.height() / 22));
            float cellWidth = rect.width() / (float) columns;
            float cellHeight = rect.height() / (float) rows;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    seed++;
                    float jitter = HubMotion.hash(seed * 11L);
                    float centerX = rect.x() + (column + 0.5F) * cellWidth;
                    float centerY = rect.y() + (row + 0.5F) * cellHeight;
                    float dx = centerX - impactX;
                    float dy = centerY - impactY;
                    float length = Math.max(1.0F, (float) Math.sqrt(dx * dx + dy * dy));
                    float speed = 70.0F + 150.0F * HubMotion.hash(seed * 17L) + 30000.0F / (length + 120.0F);
                    float velocityX = dx / length * speed;
                    float velocityY = dy / length * speed - 90.0F - 80.0F * HubMotion.hash(seed * 23L);
                    float spin = (HubMotion.hash(seed * 29L) - 0.5F) * 9.0F;
                    int color = CrystalTheme.lerp(oldTop, oldBottom, (row + 0.5F) / rows) | 0xE0000000;
                    boolean edge = row == 0 || column == 0 || row == rows - 1 || column == columns - 1;
                    this.pieces.add(new Piece(centerX, centerY, cellWidth * (0.82F + 0.3F * jitter), cellHeight * (0.82F + 0.3F * jitter),
                            velocityX, velocityY, spin, color, edge ? oldBorder | 0xFF000000 : 0));
                }
            }
        }
        // Cracks: jagged lines from the impact across the screen.
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI * 2.0D / 8.0D + HubMotion.hash(index * 41L) * 0.6D;
            float reach = Math.max(screenWidth, screenHeight) * (0.45F + 0.4F * HubMotion.hash(index * 43L));
            int segments = 7;
            float[] xs = new float[segments + 1];
            float[] ys = new float[segments + 1];
            xs[0] = impactX;
            ys[0] = impactY;
            for (int segment = 1; segment <= segments; segment++) {
                float along = reach * segment / segments;
                double bend = angle + (HubMotion.hash(index * 97L + segment) - 0.5F) * 0.7D;
                xs[segment] = impactX + (float) Math.cos(bend) * along;
                ys[segment] = impactY + (float) Math.sin(bend) * along;
            }
            this.cracks.add(new Crack(xs, ys));
        }
    }

    long elapsed(long now) {
        return now - this.startedAt;
    }

    /** The new panels are in place: their controls can come up. */
    boolean landed(long now) {
        return elapsed(now) >= DROP_START_NANOS + DROP_NANOS + DROP_STAGGER_NANOS * Math.max(0, this.incoming.size() - 1);
    }

    boolean finished(long now) {
        return landed(now) && elapsed(now) >= DROP_START_NANOS + DROP_NANOS
                + DROP_STAGGER_NANOS * Math.max(0, this.incoming.size() - 1) + SETTLE_NANOS;
    }

    /** True once the shards are flying: the old menu is no longer drawn. */
    boolean shattered(long now) {
        return elapsed(now) >= CRACK_NANOS;
    }

    void render(GuiGraphicsExtractor graphics, long now, int screenWidth, int screenHeight) {
        long elapsed = elapsed(now);
        HubDraw.isolate(graphics);
        if (elapsed < CRACK_NANOS + 220_000_000L) {
            drawCracks(graphics, elapsed);
        }
        if (elapsed >= CRACK_NANOS && elapsed < CRACK_NANOS + SHATTER_NANOS) {
            float flash = 1.0F - HubMotion.progress(elapsed - CRACK_NANOS, 240_000_000L);
            if (flash > 0.0F) {
                graphics.fill(0, 0, screenWidth, screenHeight, CrystalTheme.withAlpha(this.oldHalo, Math.round(48 * flash * flash)));
            }
            drawPieces(graphics, (elapsed - CRACK_NANOS) / 1_000_000_000.0F, HubMotion.progress(elapsed - CRACK_NANOS, SHATTER_NANOS));
        }
        HubDraw.isolate(graphics);
        if (elapsed >= DROP_START_NANOS && !finished(now)) {
            drawDrop(graphics, elapsed - DROP_START_NANOS, landed(now));
        }
        HubDraw.isolate(graphics);
    }

    private void drawCracks(GuiGraphicsExtractor graphics, long elapsed) {
        float grow = HubMotion.easeOutCubic(HubMotion.progress(elapsed, CRACK_NANOS));
        float fade = 1.0F - HubMotion.progress(elapsed - CRACK_NANOS, 220_000_000L);
        for (Crack crack : this.cracks) {
            int segments = crack.xs().length - 1;
            float shown = grow * segments;
            for (int segment = 0; segment < segments; segment++) {
                float part = Math.min(1.0F, shown - segment);
                if (part <= 0.0F) {
                    break;
                }
                float x0 = crack.xs()[segment];
                float y0 = crack.ys()[segment];
                float x1 = x0 + (crack.xs()[segment + 1] - x0) * part;
                float y1 = y0 + (crack.ys()[segment + 1] - y0) * part;
                HubDraw.line(graphics, x0, y0, x1, y1, 3.0F, CrystalTheme.withAlpha(this.oldHalo, Math.round(90 * fade)));
                HubDraw.line(graphics, x0, y0, x1, y1, 1.0F, CrystalTheme.withAlpha(0xFFFFFF, Math.round(235 * fade)));
            }
        }
        HubDraw.sparkle(graphics, this.impactX, this.impactY, 6, CrystalTheme.withAlpha(0xFFFFFF, Math.round(255 * fade)));
    }

    private void drawPieces(GuiGraphicsExtractor graphics, float seconds, float progress) {
        float gravity = 620.0F;
        float fade = 1.0F - HubMotion.smoothstep(0.55F, 1.0F, progress);
        for (Piece piece : this.pieces) {
            float x = piece.x() + piece.velocityX() * seconds;
            float y = piece.y() + piece.velocityY() * seconds + 0.5F * gravity * seconds * seconds;
            float angle = piece.spin() * seconds;
            float shrink = 1.0F - 0.35F * progress;
            if (piece.edge() != 0) {
                HubDraw.shard(graphics, x, y, piece.width() * shrink + 1.0F, piece.height() * shrink + 1.0F, angle,
                        CrystalTheme.fade(piece.edge(), fade));
            }
            HubDraw.shard(graphics, x, y, piece.width() * shrink, piece.height() * shrink, angle, CrystalTheme.fade(piece.color(), fade));
        }
    }

    /**
     * @param allLanded once every panel is down the screen draws the real ones with their
     *                  controls; from then on only the dust and the flashes are left to draw here
     */
    private void drawDrop(GuiGraphicsExtractor graphics, long dropElapsed, boolean allLanded) {
        for (int index = 0; index < this.incoming.size(); index++) {
            Rect rect = this.incoming.get(index);
            if (rect.isEmpty()) {
                continue;
            }
            long local = dropElapsed - index * DROP_STAGGER_NANOS;
            if (local < 0L) {
                continue;
            }
            float t = HubMotion.progress(local, DROP_NANOS);
            float fall = HubMotion.easeOutQuart(t);
            int offset = Math.round((1.0F - fall) * -(rect.bottom() + 30));
            if (!allLanded) {
                HubDraw.glass(graphics, rect.x(), rect.y() + offset, rect.width(), rect.height(), this.newTop, this.newBottom,
                        this.newBorder, 1.0F);
            }
            // The landing: a flash along the bottom edge and dust thrown sideways.
            float land = HubMotion.progress(local - DROP_NANOS + 90_000_000L, 360_000_000L);
            if (land > 0.0F && land < 1.0F) {
                int flash = Math.round(220 * (1.0F - land));
                graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), CrystalTheme.withAlpha(0xFFFFFF, flash));
                for (int puff = 0; puff < 12; puff++) {
                    float side = puff % 2 == 0 ? -1.0F : 1.0F;
                    float spread = HubMotion.hash(index * 131L + puff);
                    float px = rect.centerX() + side * (rect.width() * 0.2F + spread * rect.width() * 0.6F * land);
                    float py = rect.bottom() - 2 - 10.0F * land * (1.0F - land) * (1.0F + spread);
                    int size = puff % 4 == 0 ? 2 : 1;
                    graphics.fill(Math.round(px), Math.round(py), Math.round(px) + size, Math.round(py) + size,
                            CrystalTheme.withAlpha(this.newAccent, Math.round(200 * (1.0F - land))));
                }
            }
        }
    }
}
