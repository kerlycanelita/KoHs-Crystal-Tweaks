package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The way into the hub: a wall of stone behind the player's crystal, lit by its glow, and a line
 * under it that says to click the crystal. The click turns the stone into obsidian and crying
 * obsidian in a wave from the crystal outward; the blocks crack and burst, and their pieces rain
 * off to both sides while the menu comes up behind them.
 *
 * <p>Everything here is drawing: block textures from the game, lit by multiplying their colour.
 * Nothing is clickable but the crystal, which the screen tests through the stage.</p>
 */
public final class GateScene {
    private static final Identifier STONE = HubDraw.vanilla("textures/block/stone.png");
    private static final Identifier OBSIDIAN = HubDraw.vanilla("textures/block/obsidian.png");
    private static final Identifier CRYING = HubDraw.vanilla("textures/block/crying_obsidian.png");
    private static final Identifier[] CRACKS = new Identifier[10];

    static {
        for (int stage = 0; stage < CRACKS.length; stage++) {
            CRACKS[stage] = HubDraw.vanilla("textures/block/destroy_stage_" + stage + ".png");
        }
    }

    /** The stone turning, from the crystal to the farthest corner. */
    private static final long WAVE_NANOS = 340_000_000L;
    /** How long a block cracks once it has turned. */
    private static final long CRACK_NANOS = 220_000_000L;
    /** The bursts run out from the crystal too, a little behind the wave. */
    private static final long BURST_SPREAD_NANOS = 260_000_000L;
    /** How long a piece is in the air at most. */
    private static final float PIECE_LIFE = 1.35F;
    private static final float GRAVITY = 760.0F;

    private record Piece(long bornAt, float x, float y, float vx, float vy, float spin, Identifier texture, int u, int v,
            float size, float light) {
    }

    private final boolean spanish;
    private final List<Piece> pieces = new ArrayList<>();
    private int width;
    private int height;
    private int tile = 32;
    private int columns;
    private int rows;
    private int left;
    private int top;
    private long enterAt = Long.MIN_VALUE;
    private int burstX;
    private int burstY;
    private float farthest = 1.0F;
    private boolean[] burst = new boolean[0];
    private float prompt;

    public GateScene(boolean spanish) {
        this.spanish = spanish;
    }

    public void layout(int screenWidth, int screenHeight) {
        this.width = Math.max(1, screenWidth);
        this.height = Math.max(1, screenHeight);
        this.tile = Math.max(18, Math.min(40, Math.round(Math.min(this.width, this.height) / 9.0F)));
        this.columns = this.width / this.tile + 2;
        this.rows = this.height / this.tile + 2;
        this.left = (this.width - this.columns * this.tile) / 2;
        this.top = (this.height - this.rows * this.tile) / 2;
        if (this.burst.length != this.columns * this.rows) {
            this.burst = new boolean[this.columns * this.rows];
            // A resize in the middle of the burst: what had burst stays burst.
            if (entering()) {
                java.util.Arrays.fill(this.burst, true);
            }
        }
    }

    /** Where the crystal stands while the gate is up: the middle, as large as reads well. */
    public HubLayout.Rect crystalRect() {
        int size = Math.max(48, Math.min(Math.round(this.height * 0.46F), Math.min(Math.round(this.width * 0.34F), 210)));
        return new HubLayout.Rect(this.width / 2 - size / 2, this.height / 2 - size / 2 - Math.round(size * 0.08F), size, size);
    }

    /** Starts the stone turning and bursting, out from the crystal at (x, y). */
    public void enter(long now, int originX, int originY) {
        if (entering()) {
            return;
        }
        this.enterAt = now;
        this.burstX = originX;
        this.burstY = originY;
        float far = 0.0F;
        for (int corner = 0; corner < 4; corner++) {
            float dx = (corner % 2 == 0 ? 0 : this.width) - originX;
            float dy = (corner < 2 ? 0 : this.height) - originY;
            far = Math.max(far, (float) Math.sqrt(dx * dx + dy * dy));
        }
        this.farthest = Math.max(1.0F, far);
    }

    public boolean entering() {
        return this.enterAt != Long.MIN_VALUE;
    }

    /** True once the first blocks burst: the menu may start coming up behind the pieces. */
    public boolean opened(long now) {
        return entering() && now - this.enterAt >= WAVE_NANOS / 3 + CRACK_NANOS;
    }

    /** True once every block has burst and every piece has fallen away. */
    public boolean finished(long now) {
        return entering() && now - this.enterAt >= WAVE_NANOS + CRACK_NANOS + BURST_SPREAD_NANOS + (long) (PIECE_LIFE * 1e9F)
                && this.pieces.isEmpty();
    }

    private float distance(float x, float y, int fromX, int fromY) {
        float dx = x - fromX;
        float dy = y - fromY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The wall, lit by the crystal: near it the stone takes the glow's colour and brightens, far off
     * it falls to a dim grey; the light breathes with the glow.
     *
     * @param power how strong the glow is, 0 with the glow off
     */
    public void renderBackdrop(GuiGraphicsExtractor graphics, long now, int halo, float power, int crystalX, int crystalY,
            float crystalSize, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        double seconds = now / 1_000_000_000.0D;
        float breath = 0.9F + 0.1F * HubMotion.breathe(seconds, 2.6D);
        float reach = Math.max(24.0F, crystalSize * 0.95F) * (0.75F + 0.45F * power);
        int haloRed = (halo >> 16) & 255;
        int haloGreen = (halo >> 8) & 255;
        int haloBlue = halo & 255;
        HubDraw.isolate(graphics);
        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
                int index = row * this.columns + column;
                float centerX = this.left + column * this.tile + this.tile / 2.0F;
                float centerY = this.top + row * this.tile + this.tile / 2.0F;
                Identifier texture = STONE;
                int crack = -1;
                float flash = 0.0F;
                if (entering()) {
                    float fromBurst = distance(centerX, centerY, this.burstX, this.burstY) / this.farthest;
                    long turnAt = this.enterAt + (long) (WAVE_NANOS * fromBurst);
                    long burstAt = this.enterAt + WAVE_NANOS / 3 + CRACK_NANOS + (long) (BURST_SPREAD_NANOS * fromBurst);
                    if (now >= burstAt) {
                        if (!this.burst[index]) {
                            this.burst[index] = true;
                            shatter(now, centerX, centerY, index, crystalX, crystalY, reach, power);
                        }
                        continue;
                    }
                    if (now >= turnAt) {
                        texture = HubMotion.hash(index * 7919L + 13L) < 0.34F ? CRYING : OBSIDIAN;
                        flash = 1.0F - HubMotion.clamp01((now - turnAt) / 90_000_000.0F);
                        float cracking = HubMotion.clamp01((now - turnAt - 40_000_000L) / (float) Math.max(1L, burstAt - turnAt - 40_000_000L));
                        crack = Math.min(9, (int) Math.floor(cracking * 10.0F)) - 1;
                    }
                }
                float near = 1.0F / (1.0F + (float) Math.pow(distance(centerX, centerY, crystalX, crystalY) / reach, 2.0D));
                float light = Math.min(1.15F, 0.24F + (0.18F + 0.78F * power) * near * breath);
                float tint = 0.6F * near * Math.min(1.0F, power + 0.2F);
                int red = Math.min(255, Math.round((255 + (haloRed - 255) * tint) * light + 255 * flash));
                int green = Math.min(255, Math.round((255 + (haloGreen - 255) * tint) * light + 255 * flash));
                int blue = Math.min(255, Math.round((255 + (haloBlue - 255) * tint) * light + 255 * flash));
                int color = (Math.round(255 * alpha) << 24) | (red << 16) | (green << 8) | blue;
                HubDraw.texture(graphics, texture, 16, 16, centerX, centerY, this.tile, this.tile, 0.0F, color);
                if (crack >= 0) {
                    HubDraw.texture(graphics, CRACKS[crack], 16, 16, centerX, centerY, this.tile, this.tile, 0.0F,
                            CrystalTheme.fade(0xE0FFFFFF, alpha));
                }
            }
        }
        HubDraw.isolate(graphics);
        // The glow over the wall, and a vignette that keeps the edges quiet.
        HubDraw.glowDisc(graphics, crystalX, crystalY, Math.round(reach * 1.25F), halo, (0.18F + 0.32F * power) * alpha * breath);
        int shade = Math.round(Math.min(this.width, this.height) * 0.22F);
        graphics.fillGradient(0, 0, this.width, shade, CrystalTheme.fade(0xB0050208, alpha), 0x00050208);
        graphics.fillGradient(0, this.height - shade, this.width, this.height, 0x00050208, CrystalTheme.fade(0xC0050208, alpha));
        HubDraw.isolate(graphics);
    }

    /** Three or four pieces of a block, thrown to the side away from the middle, then falling. */
    private void shatter(long now, float centerX, float centerY, int index, int crystalX, int crystalY, float reach, float power) {
        boolean crying = HubMotion.hash(index * 7919L + 13L) < 0.34F;
        Identifier texture = crying ? CRYING : OBSIDIAN;
        float side = centerX < this.burstX ? -1.0F : 1.0F;
        float near = 1.0F / (1.0F + (float) Math.pow(distance(centerX, centerY, crystalX, crystalY) / reach, 2.0D));
        float light = Math.min(1.0F, 0.45F + 0.55F * near * Math.max(0.3F, power));
        float spread = Math.abs(centerX - this.burstX) / Math.max(1.0F, this.width / 2.0F);
        for (int quarter = 0; quarter < 4; quarter++) {
            float roll = HubMotion.hash(index * 31L + quarter * 977L);
            if (quarter == 3 && roll < 0.35F) {
                continue;
            }
            int u = quarter % 2 * 8;
            int v = quarter / 2 * 8;
            float offsetX = (quarter % 2 == 0 ? -0.25F : 0.25F) * this.tile;
            float offsetY = (quarter / 2 == 0 ? -0.25F : 0.25F) * this.tile;
            float vx = side * (70.0F + 240.0F * spread + 110.0F * roll) + offsetX * 2.0F;
            float vy = -(50.0F + 170.0F * HubMotion.hash(index * 131L + quarter)) + offsetY * 1.5F;
            float spin = (roll - 0.5F) * 14.0F;
            this.pieces.add(new Piece(now, centerX + offsetX, centerY + offsetY, vx, vy, spin, texture, u, v,
                    this.tile * (0.42F + 0.12F * roll), light));
        }
    }

    /** The pieces in the air: drawn over the menu, as they fall in front of it. */
    public void renderDebris(GuiGraphicsExtractor graphics, long now) {
        if (this.pieces.isEmpty()) {
            return;
        }
        HubDraw.isolate(graphics);
        this.pieces.removeIf(piece -> (now - piece.bornAt()) / 1e9F > PIECE_LIFE);
        for (Piece piece : this.pieces) {
            float t = (now - piece.bornAt()) / 1e9F;
            float x = piece.x() + piece.vx() * t;
            float y = piece.y() + piece.vy() * t + 0.5F * GRAVITY * t * t;
            if (x < -piece.size() || x > this.width + piece.size() || y > this.height + piece.size()) {
                continue;
            }
            float fade = 1.0F - HubMotion.smoothstep(0.65F, 1.0F, t / PIECE_LIFE);
            int shade = Math.round(255 * piece.light());
            int color = (Math.round(255 * fade) << 24) | (shade << 16) | (shade << 8) | shade;
            HubDraw.piece(graphics, piece.texture(), piece.u(), piece.v(), 8, 8, 16, 16, x, y, piece.size(), piece.size(),
                    piece.spin() * t, color);
        }
        HubDraw.isolate(graphics);
    }

    /** A burst of the glow's light at the crystal as the blocks go. */
    public void renderBlast(GuiGraphicsExtractor graphics, long now, int halo) {
        if (!entering()) {
            return;
        }
        float t = HubMotion.clamp01((now - this.enterAt - WAVE_NANOS / 3 - CRACK_NANOS + 60_000_000L) / 420_000_000.0F);
        if (t <= 0.0F || t >= 1.0F) {
            return;
        }
        float strength = (float) Math.sin(t * Math.PI);
        HubDraw.isolate(graphics);
        HubDraw.glowDisc(graphics, this.burstX, this.burstY, Math.round(Math.min(this.width, this.height) * (0.25F + 0.55F * t)), halo,
                0.7F * strength);
        HubDraw.ring(graphics, this.burstX, this.burstY, Math.min(this.width, this.height) * (0.08F + 0.7F * t), 2,
                CrystalTheme.withAlpha(0xFFFFFF, Math.round(200 * (1.0F - t))));
        HubDraw.isolate(graphics);
    }

    /** "Click the crystal to enter", breathing under it until the click. */
    public void renderPrompt(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int centerX, int y, float alpha,
            boolean hovered) {
        this.prompt = HubMotion.damp(this.prompt, entering() ? 0.0F : 1.0F, frameMillis, 120.0F);
        float shown = this.prompt * alpha;
        if (shown <= 0.02F) {
            return;
        }
        double seconds = now / 1_000_000_000.0D;
        float pulse = 0.62F + 0.38F * HubMotion.breathe(seconds, 1.7D);
        String text = this.spanish ? "Haz clic en el cristal para entrar" : "Click the crystal to enter";
        int color = CrystalTheme.lerp(0xFFE7D4F5, 0xFFFFFFFF, hovered ? 1.0F : 0.0F);
        HubDraw.isolate(graphics);
        int textWidth = font.width(text);
        HubDraw.glass(graphics, centerX - textWidth / 2 - 10, y - 4, textWidth + 20, 17, 0xB0140820, 0xB00A0410,
                CrystalTheme.withAlpha(0xC488FF, Math.round(120 + 100 * pulse)), shown);
        CrystalUi.centered(graphics, font, text, centerX, y + 1, CrystalTheme.fade(color, shown * (hovered ? 1.0F : pulse)));
        HubDraw.isolate(graphics);
    }
}
