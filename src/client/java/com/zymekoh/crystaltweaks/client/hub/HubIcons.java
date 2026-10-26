package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.hub.GrimoireIcons.Art;
import com.zymekoh.crystaltweaks.client.hub.GrimoireIcons.Kind;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The live icons of the settings: the game's own art, always moving, each on its own beat so the
 * rows never pulse in unison. Every sprite comes from {@link GrimoireIcons}: read from the game's
 * built-in pack and redrawn in the book's colours, so no resource pack changes them. A cube is a
 * block's face folded into a small isometric cube; a particle is the game's particle sprite, tinted
 * as the game tints it; the crystal is the End Crystal's own sprite. Nothing here is drawn from
 * lines and fills, and nothing allocates per frame.
 */
public final class HubIcons {
    private static final Art GLOWSTONE = block("glowstone");
    private static final Art OBSIDIAN = block("obsidian");
    private static final Art CRYING_OBSIDIAN = block("crying_obsidian");
    private static final Art GRASS_TOP = block("grass_block_top");
    private static final Art GRASS_SIDE = block("grass_block_side");
    private static final Art TNT_TOP = block("tnt_top");
    private static final Art TNT_SIDE = block("tnt_side");
    private static final Art DIAMOND_BLOCK = block("diamond_block");
    private static final Art END_CRYSTAL = GrimoireIcons.art("item/end_crystal", Kind.ITEM);
    private static final Art ENDER_EYE = GrimoireIcons.art("item/ender_eye", Kind.ITEM);
    private static final Art SWORD = GrimoireIcons.art("item/netherite_sword", Kind.ITEM);
    private static final Art COMPARATOR = GrimoireIcons.art("item/comparator", Kind.ITEM);
    private static final Art NOTE = GrimoireIcons.art("particle/note", Kind.PLAIN);
    private static final Art[] EXPLOSION = new Art[16];
    private static final Art[] CLOCK = new Art[64];
    /** The colours a note particle takes, low to high, as the note block's. */
    private static final int[] NOTE_COLORS = {0xFF77D700, 0xFF00C7FF, 0xFF9B4BFF, 0xFFFF4FC8, 0xFFFFC800};

    static {
        for (int frame = 0; frame < EXPLOSION.length; frame++) {
            EXPLOSION[frame] = GrimoireIcons.art(String.format(Locale.ROOT, "particle/explosion_%d", frame), Kind.PLAIN);
        }
        for (int frame = 0; frame < CLOCK.length; frame++) {
            CLOCK[frame] = GrimoireIcons.art(String.format(Locale.ROOT, "item/clock_%02d", frame), Kind.ITEM);
        }
    }

    private HubIcons() {
    }

    private static Art block(String name) {
        return GrimoireIcons.art("block/" + name, Kind.BLOCK);
    }

    /** Makes every sprite these icons always draw, as the settings open, not in the middle of an animation. */
    public static void warm() {
        for (Art art : new Art[] {GLOWSTONE, OBSIDIAN, CRYING_OBSIDIAN, GRASS_TOP, GRASS_SIDE, TNT_TOP, TNT_SIDE, DIAMOND_BLOCK, END_CRYSTAL,
                ENDER_EYE, SWORD, COMPARATOR, NOTE}) {
            art.id();
        }
        for (Art art : EXPLOSION) {
            art.id();
        }
        for (Art art : CLOCK) {
            art.id();
        }
    }

    private static double seconds() {
        return System.nanoTime() / 1_000_000_000.0D;
    }

    private static float alpha(int color) {
        return ((color >>> 24) & 255) / 255.0F;
    }

    private static int opaque(int color) {
        return (color & 0xFF000000) | 0xFFFFFF;
    }

    /** The End Crystal, {@code scale} times the icon, bobbing a little. */
    private static void crystal(net.minecraft.client.gui.GuiGraphicsExtractor graphics, float centerX, float centerY, int size, float scale,
            int color) {
        GrimoireIcons.drawItem(graphics, END_CRYSTAL, centerX, centerY, (size + 1.0F) * scale, 0.0F, color);
    }

    /** Glowstone, breathing light. */
    public static HubButton.Icon glowstone(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            float breathe = 0.5F + 0.5F * (float) Math.sin(seconds * 3.1D);
            HubDraw.glowDisc(graphics, Math.round(x + size / 2.0F), Math.round(y + size / 2.0F), Math.round(size * 0.9F),
                    0xFFFFD27A, 0.22F + 0.28F * breathe * alpha(color));
            GrimoireIcons.drawCube(graphics, GLOWSTONE, GLOWSTONE, x + size / 2.0F, y + size / 2.0F, size * 0.6F, alpha(color), 0xFFFFFFFF);
        };
    }

    /** The game's explosion, its frames big enough to read, then the crystal waiting to go off again. */
    public static HubButton.Icon explosion(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            double cycle = seconds % 1.6D;
            int frame = (int) (cycle / 0.06D);
            // The last frames are a few faint pixels: a row with its icon alone would look empty.
            if (frame >= 12) {
                // Between blasts: the crystal waits to go off.
                crystal(graphics, x + size / 2.0F, y + size / 2.0F + (float) Math.sin(seconds * 2.6D) * 0.6F, size, 1.0F, opaque(color));
                return;
            }
            GrimoireIcons.drawItem(graphics, EXPLOSION[frame], x + size / 2.0F, y + size / 2.0F, size + 4, 0.0F,
                    CrystalTheme.withAlpha(0xFFE2C8FF, Math.round(255 * alpha(color))));
        };
    }

    /** A note particle rising and fading, in the note block's colours. */
    public static HubButton.Icon note(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            double t = seconds % 1.4D / 1.4D;
            int noteColor = NOTE_COLORS[(int) (seconds / 1.4D) % NOTE_COLORS.length];
            float rise = (float) t * size * 0.35F;
            // It never fades out entirely: a row showing its icon alone must not look empty.
            float fade = 0.35F + 0.65F * (float) Math.min(1.0D, Math.min(t * 5.0D, (1.0D - t) * 3.0D));
            GrimoireIcons.drawItem(graphics, NOTE, x + size / 2.0F, y + size / 2.0F + size * 0.15F - rise, size * 0.9F,
                    (float) Math.sin(seconds * 3.0D) * 0.15F, CrystalTheme.withAlpha(noteColor, Math.round(255 * fade * alpha(color))));
        };
    }

    /** Obsidian, rocking a little; crying obsidian now and then: the block your crystals stand on. */
    public static HubButton.Icon obsidian(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            float bob = (float) Math.sin(seconds * 2.4D) * 0.7F;
            Art face = (int) (seconds / 2.3D) % 3 == 2 ? CRYING_OBSIDIAN : OBSIDIAN;
            // Obsidian is nearly black: a violet glow behind it, its crying light, keeps it on the dark.
            HubDraw.glowDisc(graphics, Math.round(x + size / 2.0F), Math.round(y + size / 2.0F), Math.round(size * 0.85F), 0xFF9B4BFF,
                    (0.3F + 0.12F * (float) Math.sin(seconds * 2.0D)) * alpha(color));
            GrimoireIcons.drawCube(graphics, face, face, x + size / 2.0F, y + size / 2.0F + bob, size * 0.6F, alpha(color), 0xFFFFFFFF);
        };
    }

    /** What a crystal can be drawn as: an ender eye, grass, TNT, diamond, one after another. */
    public static HubButton.Icon converter(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            int step = (int) (seconds / 0.9D) % 4;
            float pop = (float) Math.min(1.0D, (seconds % 0.9D) / 0.12D);
            float scale = 0.7F + 0.3F * HubMotion.easeOutBack(pop);
            float cx = x + size / 2.0F;
            float cy = y + size / 2.0F;
            switch (step) {
                case 0 -> GrimoireIcons.drawItem(graphics, ENDER_EYE, cx, cy, size * scale, (float) Math.sin(seconds * 2.0D) * 0.2F, opaque(color));
                case 1 -> GrimoireIcons.drawCube(graphics, GRASS_TOP, GRASS_SIDE, cx, cy, size * 0.6F * scale, alpha(color), 0xFFFFFFFF);
                case 2 -> GrimoireIcons.drawCube(graphics, TNT_TOP, TNT_SIDE, cx, cy, size * 0.6F * scale, alpha(color), 0xFFFFFFFF);
                default -> GrimoireIcons.drawCube(graphics, DIAMOND_BLOCK, DIAMOND_BLOCK, cx, cy, size * 0.6F * scale, alpha(color), 0xFFFFFFFF);
            }
        };
    }

    /** A netherite sword, swinging: the fight waiting in Crystal Practice. */
    public static HubButton.Icon sword(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            double t = seconds % 1.8D;
            float swing = t < 0.25D ? (float) (-0.9D + 1.6D * HubMotion.easeOutCubic((float) (t / 0.25D))) : 0.7F - (float) Math.min(0.7D, (t - 0.25D) * 0.9D);
            GrimoireIcons.drawItem(graphics, SWORD, x + size / 2.0F, y + size / 2.0F, size + 1, swing * 0.5F, opaque(color));
        };
    }

    /** A comparator, its front torch flickering: the tools that measure. */
    public static HubButton.Icon comparator(float phase) {
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase;
            GrimoireIcons.drawItem(graphics, COMPARATOR, x + size / 2.0F, y + size / 2.0F, size + 1, (float) Math.sin(seconds * 1.7D) * 0.08F,
                    opaque(color));
            float flicker = 0.5F + 0.5F * (float) Math.sin(seconds * 9.0D);
            HubDraw.glowDisc(graphics, Math.round(x + size * 0.5F), Math.round(y + size * 0.25F), Math.max(2, size / 4), 0xFFFF3030,
                    0.35F * flicker * alpha(color));
        };
    }

    /** The KoHs mark, breathing. */
    public static HubButton.Icon kohs(float phase) {
        return (graphics, x, y, size, color) -> {
            float breathe = 1.0F + 0.06F * (float) Math.sin((seconds() + phase) * 2.0D);
            HubDraw.texture(graphics, KohsPanel.mark(), 96, 96, x + size / 2.0F, y + size / 2.0F, size * breathe, size * breathe, 0.0F,
                    opaque(color));
        };
    }

    /** Any item sprite, bobbing on its own beat. */
    public static HubButton.Icon item(String name, float phase) {
        Art art = GrimoireIcons.art("item/" + name, Kind.ITEM);
        return (graphics, x, y, size, color) -> {
            double seconds = seconds() + phase * 3.0D;
            float bob = (float) Math.sin(seconds * 2.6D) * 0.7F;
            GrimoireIcons.drawItem(graphics, art, x + size / 2.0F, y + size / 2.0F + bob, size + 1.0F, (float) Math.sin(seconds * 1.9D) * 0.1F,
                    opaque(color));
        };
    }

    /** The clock, its dial turning through the game's own 64 frames: time, turning, animation. */
    public static HubButton.Icon clock(float phase) {
        return (graphics, x, y, size, color) -> {
            int frame = (int) (((seconds() + phase * 4.0D) / 6.0D % 1.0D) * CLOCK.length);
            GrimoireIcons.drawItem(graphics, CLOCK[Math.max(0, Math.min(CLOCK.length - 1, frame))], x + size / 2.0F, y + size / 2.0F,
                    size + 1.0F, 0.0F, opaque(color));
        };
    }

    /** An item sprite that grows and shrinks on its own beat: size. */
    public static HubButton.Icon pulse(String name, float phase) {
        Art art = GrimoireIcons.art("item/" + name, Kind.ITEM);
        return (graphics, x, y, size, color) -> {
            float scale = 0.78F + 0.27F * (0.5F + 0.5F * (float) Math.sin((seconds() + phase * 3.0D) * 2.6D));
            GrimoireIcons.drawItem(graphics, art, x + size / 2.0F, y + size / 2.0F, (size + 1.0F) * scale, 0.0F, opaque(color));
        };
    }

    /**
     * A block as a small cube in a colour that can change, bobbing: a layer of the crystal in its
     * colour. The block is kept as it is, so the colour lands on it as it would on the crystal.
     */
    public static HubButton.Icon tinted(String name, IntSupplier tint, float phase) {
        Art art = GrimoireIcons.art("block/" + name, Kind.PLAIN);
        return (graphics, x, y, size, color) -> {
            float bob = (float) Math.sin((seconds() + phase * 3.0D) * 2.2D) * 0.6F;
            GrimoireIcons.drawCube(graphics, art, art, x + size / 2.0F, y + size / 2.0F + bob, size * 0.6F, alpha(color), tint.getAsInt() | 0xFF000000);
        };
    }

    /** A block with a top of its own as a small cube, bobbing: a respawn anchor, a grass block. */
    public static HubButton.Icon cube(String top, String side, float phase) {
        Art topArt = block(top);
        Art sideArt = block(side);
        return (graphics, x, y, size, color) -> {
            float bob = (float) Math.sin((seconds() + phase * 3.0D) * 2.2D) * 0.6F;
            GrimoireIcons.drawCube(graphics, topArt, sideArt, x + size / 2.0F, y + size / 2.0F + bob, size * 0.6F, alpha(color), 0xFFFFFFFF);
        };
    }

    /** The End Crystal growing and shrinking: size. */
    public static HubButton.Icon growing(float phase) {
        return (graphics, x, y, size, color) -> {
            float scale = 0.72F + 0.4F * (0.5F + 0.5F * (float) Math.sin((seconds() + phase * 3.0D) * 2.6D));
            crystal(graphics, x + size / 2.0F, y + size / 2.0F, size, scale, opaque(color));
        };
    }

    /** The End Crystal gone the moment it is hit, in a white burst, and back: the client-side crystal. */
    public static HubButton.Icon shatter(float phase) {
        return (graphics, x, y, size, color) -> {
            float alpha = alpha(color);
            if (alpha < 0.05F) {
                return;
            }
            double cycle = (seconds() + phase) % 1.9D;
            float centerX = x + size / 2.0F;
            float centerY = y + size / 2.0F;
            if (cycle < 0.9D) {
                crystal(graphics, centerX, centerY, size, 1.0F, opaque(color));
                return;
            }
            if (cycle < 1.4D) {
                // Hit: gone at once, a flash and its glass flying.
                float t = (float) ((cycle - 0.9D) / 0.5D);
                HubDraw.glowDisc(graphics, Math.round(centerX), Math.round(centerY), Math.round(size * 0.6F), 0xFFFFFFFF, 0.8F * (1.0F - t) * alpha);
                float spread = size * (0.1F + 0.5F * HubMotion.easeOutCubic(t));
                for (int shard = 0; shard < 6; shard++) {
                    double angle = shard * Math.PI / 3.0D + 0.4D;
                    HubDraw.shard(graphics, centerX + (float) Math.cos(angle) * spread, centerY + (float) Math.sin(angle) * spread,
                            1.5F, 3.0F, (float) angle, CrystalTheme.withAlpha(0xFFE6D2FF, Math.round(230 * alpha * (1.0F - t))));
                }
                return;
            }
            // The next one grows in.
            float grow = HubMotion.easeOutBack((float) ((cycle - 1.4D) / 0.5D));
            crystal(graphics, centerX, centerY, size, Math.max(0.05F, grow), opaque(color));
        };
    }

    /** The player's crystal, pale, as the stand-in drawn before the server's arrives. */
    public static CrystalAppearance ghostLook() {
        CrystalAppearance look = MiniCrystal.spinning(CrystalVisualConfig.visuals(false));
        look.outerColor = pale(look.outerColor);
        look.innerColor = pale(look.innerColor);
        look.coreColor = pale(look.coreColor);
        return look;
    }

    private static int pale(int color) {
        return color == -1 ? 0xFFE6D2FF : CrystalTheme.lerp(color | 0xFF000000, 0xFFF4EEFA, 0.65F);
    }

    /** A pale stand-in crystal breathing over obsidian, a violet light round it: the ghost crystals. */
    public static HubButton.Icon ghost(float phase) {
        return (graphics, x, y, size, color) -> {
            float alpha = alpha(color);
            if (alpha < 0.05F) {
                return;
            }
            float breath = 0.5F + 0.5F * (float) Math.sin((seconds() + phase) * 3.2D);
            GrimoireIcons.drawCube(graphics, OBSIDIAN, OBSIDIAN, x + size / 2.0F, y + size * 0.86F, size * 0.36F, alpha, 0xFFFFFFFF);
            HubDraw.glowDisc(graphics, Math.round(x + size / 2.0F), Math.round(y + size * 0.34F), Math.round(size * 0.6F), 0xFFC880FF,
                    (0.2F + 0.35F * breath) * alpha);
            // Pale and half seen: only the drawing of a crystal.
            crystal(graphics, x + size / 2.0F, y + size * 0.36F, size, 0.8F + 0.12F * breath,
                    CrystalTheme.withAlpha(0xFFF4EEFA, Math.round(255 * alpha * (0.55F + 0.25F * breath))));
        };
    }

    /** The End Crystal dropping onto obsidian the moment the old one is gone: placement at the server's pace. */
    public static HubButton.Icon drop(float phase) {
        return (graphics, x, y, size, color) -> {
            float alpha = alpha(color);
            if (alpha < 0.05F) {
                return;
            }
            double cycle = (seconds() + phase) % 1.5D;
            float centerX = x + size / 2.0F;
            GrimoireIcons.drawCube(graphics, OBSIDIAN, OBSIDIAN, centerX, y + size * 0.86F, size * 0.36F, alpha, 0xFFFFFFFF);
            float fall = (float) Math.min(1.0D, cycle / 0.45D);
            float landed = (float) Math.max(0.0D, Math.min(1.0D, (cycle - 0.45D) / 0.3D));
            if (fall >= 1.0F && landed < 1.0F) {
                HubDraw.ring(graphics, centerX, y + size * 0.64F, size * (0.2F + 0.45F * landed), 1,
                        CrystalTheme.withAlpha(0xFFC880FF, Math.round(220 * alpha * (1.0F - landed))));
            }
            if (cycle > 1.3D) {
                // Broken: the next one is on its way.
                return;
            }
            float offset = -size * 0.3F * (1.0F - fall * fall) - size * 0.2F;
            crystal(graphics, centerX, y + size / 2.0F + offset, size, 0.85F, opaque(color));
        };
    }

    /** Any block as a small cube, bobbing. */
    public static HubButton.Icon block(String name, float phase) {
        Art art = block(name);
        return (graphics, x, y, size, color) -> {
            float bob = (float) Math.sin((seconds() + phase * 3.0D) * 2.2D) * 0.6F;
            GrimoireIcons.drawCube(graphics, art, art, x + size / 2.0F, y + size / 2.0F + bob, size * 0.6F, alpha(color), 0xFFFFFFFF);
        };
    }
}
