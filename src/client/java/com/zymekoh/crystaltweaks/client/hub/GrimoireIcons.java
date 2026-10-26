package com.zymekoh.crystaltweaks.client.hub;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * The settings' small art: Minecraft's own sprites, read from the game's built-in pack and kept as
 * textures of the mod's own, so no resource pack changes them.
 *
 * <p>An item or a block is redrawn in the book's colours: its light stretched to its full range, so
 * obsidian shows as well as glowstone, then mapped through the dark-to-bone palette a little under
 * half over its own colours, so a glowstone still reads as glowstone. Items get a black outline and
 * a pale rim light on their upper-left edges; blocks are folded into small cubes. A sprite the game
 * tints as it draws it (a note, an explosion, white glass) is kept as it is, to be tinted the same
 * way. If the built-in pack cannot be read the plain sprite is drawn instead.</p>
 *
 * <p>Here too are the soft shapes the settings light themselves with (a glow, a disc, a mote of
 * dust): each one texture, drawn smoothly at any size, so a glow is one element and not hundreds.</p>
 */
public final class GrimoireIcons {
    /** How a sprite is kept: redrawn as an item or a block's face, or as it is. */
    public enum Kind { ITEM, BLOCK, PLAIN }

    /** One of the game's sprites as the book keeps it, made the first time it is drawn. */
    public static final class Art {
        private final String path;
        private final Kind kind;
        private Identifier id;
        private int width = 16;
        private int height = 16;

        private Art(String path, Kind kind) {
            this.path = path;
            this.kind = kind;
        }

        public Identifier id() {
            if (this.id == null) {
                make(this);
            }
            return this.id;
        }

        /** The kept texture's own pixels. */
        public int width() {
            id();
            return this.width;
        }

        public int height() {
            id();
            return this.height;
        }

        /** Whether it is the redrawn item on its 18-pixel canvas, rather than a plain sprite. */
        public boolean canvas() {
            return this.kind == Kind.ITEM && redrawn(id());
        }
    }

    public enum Sprite {
        END_CRYSTAL("item/end_crystal"),
        ENDER_EYE("item/ender_eye"),
        NOTE_BLOCK("block/note_block"),
        GLOWSTONE("block/glowstone"),
        NETHER_STAR("item/nether_star"),
        OBSIDIAN("block/obsidian"),
        AMETHYST_SHARD("item/amethyst_shard"),
        COMPARATOR("item/comparator"),
        NETHERITE_SWORD("item/netherite_sword"),
        TOTEM("item/totem_of_undying"),
        CLOCK("item/clock_00");

        final String path;
        private Art art;

        Sprite(String path) {
            this.path = path;
        }

        /** A block's face, drawn as a cube; an item, drawn flat. */
        public boolean block() {
            return this.path.startsWith("block/");
        }

        public Art art() {
            if (this.art == null) {
                this.art = GrimoireIcons.art(this.path, block() ? Kind.BLOCK : Kind.ITEM);
            }
            return this.art;
        }
    }

    /** The book's palette, from the shadow to the brightest page: what each sprite is mapped through. */
    private static final int[] PALETTE = {0x07030C, 0x2A0F45, 0x6A2CB8, 0xB47CF2, 0xF4EEFA};
    private static final float MAP_SHARE = 0.45F;
    /** Where a sprite's darkest pixel lands on the palette once its light is stretched. */
    private static final float STRETCH_FLOOR = 0.25F;
    private static final int OUTLINE = 0xFF05020A;
    private static final int RIM = 0xF4EEFA;
    /** The sprite's 16 pixels with a pixel of outline round them. */
    public static final int CANVAS = 18;

    private static final Map<String, Art> ARTS = new HashMap<>();
    /** The dotted strip the rows' leaders are cut from. */
    public static final int DOTS_LENGTH = 64;
    private static final Identifier DOTS = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/dots");
    /** A disc of light fading from its centre to nothing at its edge. */
    public static final int GLOW_SIZE = 64;
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/glow");
    /** A solid round disc with a soft rim, for knobs. */
    public static final int DISC_SIZE = 32;
    private static final Identifier DISC = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/disc");
    /** A soft speck of dust, bright in its middle. */
    public static final int MOTE_SIZE = 8;
    private static final Identifier MOTE = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/mote");
    /** One repeat of hazard tape: red stripes leaning on black, tiled along a strip. */
    public static final int HAZARD_WIDTH = 10;
    public static final int HAZARD_HEIGHT = 5;
    private static final Identifier HAZARD = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/hazard");
    private static boolean shapesMade;
    /** The curtain wall behind the crystal: the mod's own stone, eight blocks by four of 16 pixels, tiled. */
    public static final int WALL_WIDTH = 128;
    public static final int WALL_HEIGHT = 64;
    private static final Identifier WALL = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/curtain_wall");
    /** The coping on the wall and its merlons, one block of 16 pixels, tiled. */
    private static final Identifier COPING = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/merlon_cap");
    /** The floor in front of the wall: the game's obsidian, redrawn, one block of 16 pixels, tiled. */
    private static final Identifier FLOOR = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire/floor");
    private static boolean courtyardMade;

    /** How a texture is sampled: pixel art as it is, smooth shapes scaled without steps, strips repeated along their length. */
    private enum Sampling { PIXEL, SMOOTH, TILED }

    private GrimoireIcons() {
    }

    /** The sprite at {@code path} ("item/ender_eye", "particle/note"), kept as {@code kind}. Look it up once, not every frame. */
    public static Art art(String path, Kind kind) {
        return ARTS.computeIfAbsent(kind.ordinal() + path, ignored -> new Art(path, kind));
    }

    /** Makes the book's art at once, as the settings open, rather than in the middle of an animation. */
    public static void warm() {
        shapes();
        courtyard();
        for (Sprite sprite : Sprite.values()) {
            sprite.art().id();
        }
    }

    public static Identifier dots() {
        shapes();
        return DOTS;
    }

    public static Identifier glow() {
        shapes();
        return GLOW;
    }

    public static Identifier disc() {
        shapes();
        return DISC;
    }

    public static Identifier mote() {
        shapes();
        return MOTE;
    }

    public static Identifier hazard() {
        shapes();
        return HAZARD;
    }

    public static Identifier wall() {
        courtyard();
        return WALL;
    }

    public static Identifier coping() {
        courtyard();
        return COPING;
    }

    public static Identifier floor() {
        courtyard();
        return FLOOR;
    }

    /**
     * The wall and its coping ship inside the mod and are read straight from its jar, as its letters
     * are: no resource pack reaches them. The floor is the game's own obsidian from its built-in pack.
     */
    private static void courtyard() {
        if (courtyardMade) {
            return;
        }
        courtyardMade = true;
        register(WALL, shipped("curtain_wall.png", WALL_WIDTH, WALL_HEIGHT, 0xFF231A2E), Sampling.TILED);
        register(COPING, shipped("merlon_cap.png", 16, 16, 0xFF2C2238), Sampling.TILED);
        register(FLOOR, obsidian(), Sampling.TILED);
    }

    /** One of the mod's own textures; a plain stone of {@code colour} if it cannot be read. */
    private static NativeImage shipped(String name, int width, int height, int colour) {
        try (InputStream in = GrimoireIcons.class.getResourceAsStream("/crystal_tweaks_fixed/textures/" + name)) {
            if (in != null) {
                return NativeImage.read(in);
            }
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Crystal Tweaks could not read its {} texture", name, exception);
        }
        return plain(width, height, colour);
    }

    private static NativeImage plain(int width, int height, int colour) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, colour);
            }
        }
        return image;
    }

    /** The game's obsidian through the book's palette; a dark stone if the built-in pack cannot be read. */
    private static NativeImage obsidian() {
        Identifier plain = Identifier.withDefaultNamespace("textures/block/obsidian.png");
        IoSupplier<InputStream> resource = Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, plain);
        if (resource != null) {
            try (InputStream in = resource.get(); NativeImage source = NativeImage.read(in)) {
                return gothic(source, false);
            } catch (Exception exception) {
                CrystalTweaksClient.LOGGER.warn("Crystal Tweaks could not keep the obsidian floor", exception);
            }
        }
        return plain(16, 16, 0xFF140C1E);
    }

    /** The dotted strip, the glow, the disc and the mote, drawn once into textures of their own. */
    private static void shapes() {
        if (shapesMade) {
            return;
        }
        shapesMade = true;
        NativeImage dots = new NativeImage(DOTS_LENGTH, 1, true);
        for (int x = 0; x < DOTS_LENGTH; x += 2) {
            dots.setPixel(x, 0, 0xFFFFFFFF);
        }
        register(DOTS, dots, Sampling.TILED);
        NativeImage hazard = new NativeImage(HAZARD_WIDTH, HAZARD_HEIGHT, true);
        for (int y = 0; y < HAZARD_HEIGHT; y++) {
            for (int x = 0; x < HAZARD_WIDTH; x++) {
                hazard.setPixel(x, y, Math.floorMod(x - y, HAZARD_WIDTH) < HAZARD_WIDTH / 2 ? 0xF0FF2A40 : 0xE0120104);
            }
        }
        register(HAZARD, hazard, Sampling.TILED);
        register(GLOW, round(GLOW_SIZE, distance -> 0.82F * (float) Math.pow(Math.max(0.0F, 1.0F - distance), 1.15D)), Sampling.SMOOTH);
        // Solid to its rim, where a texel and a half of soft edge keeps it round at any size.
        register(DISC, round(DISC_SIZE, distance -> Math.max(0.0F, Math.min(1.0F, (1.0F - distance) * DISC_SIZE / 3.0F))), Sampling.SMOOTH);
        register(MOTE, round(MOTE_SIZE, distance -> (float) Math.pow(Math.max(0.0F, 1.0F - distance), 1.6D)), Sampling.SMOOTH);
    }

    private interface Falloff {
        float alpha(float distance);
    }

    /** A white square texture whose opacity is {@code falloff} of the distance from its centre, 1 at its edge. */
    private static NativeImage round(int size, Falloff falloff) {
        NativeImage image = new NativeImage(size, size, true);
        float half = size / 2.0F;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5F - half) / half;
                float dy = (y + 0.5F - half) / half;
                int alpha = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, falloff.alpha((float) Math.sqrt(dx * dx + dy * dy)))));
                image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
            }
        }
        return image;
    }

    /** Hands {@code image} to the game as a texture, sampled as {@code sampling} says. */
    private static void register(Identifier id, NativeImage image, Sampling sampling) {
        DynamicTexture texture = sampling == Sampling.PIXEL ? new DynamicTexture(id::toString, image) : new DynamicTexture(id::toString, image) {
            {
                this.sampler = sampling == Sampling.SMOOTH ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        : RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            }
        };
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** The redrawn sprite's texture; the plain sprite if it cannot be redrawn. */
    public static Identifier texture(Sprite sprite) {
        return sprite.art().id();
    }

    /** Whether a texture is one the book made, rather than the game's own. */
    public static boolean redrawn(Identifier texture) {
        return texture.getNamespace().equals("crystal_tweaks");
    }

    private static void make(Art art) {
        Identifier plain = Identifier.withDefaultNamespace("textures/" + art.path + ".png");
        // Until it is made, and if it cannot be, the game's own sprite.
        art.id = plain;
        Minecraft minecraft = Minecraft.getInstance();
        IoSupplier<InputStream> resource = minecraft.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, plain);
        if (resource == null) {
            return;
        }
        try (InputStream in = resource.get(); NativeImage source = NativeImage.read(in)) {
            NativeImage image = switch (art.kind) {
                case ITEM -> gothic(source, true);
                case BLOCK -> gothic(source, false);
                case PLAIN -> copy(source);
            };
            Identifier id = Identifier.fromNamespaceAndPath("crystal_tweaks",
                    "grimoire/" + art.kind.name().toLowerCase(Locale.ROOT) + "/" + art.path);
            register(id, image, Sampling.PIXEL);
            art.width = image.getWidth();
            art.height = image.getHeight();
            art.id = id;
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Crystal Tweaks could not keep the {} sprite", art.path, exception);
        }
    }

    /** The sprite as it is; only its first frame, if it is an animated strip. */
    private static NativeImage copy(NativeImage source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        NativeImage image = new NativeImage(source.getWidth(), size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                image.setPixel(x, y, source.getPixel(x, y));
            }
        }
        return image;
    }

    /**
     * The sprite through the palette, its light stretched first. An item comes on its 18-pixel canvas,
     * outlined and rim lit; a block's face stays 16 pixels, to be folded into a cube.
     */
    private static NativeImage gothic(NativeImage source, boolean item) {
        int step = Math.max(1, source.getWidth() / 16);
        int[] argb = new int[16 * 16];
        float darkest = 1.0F;
        float brightest = 0.0F;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = sample(source, x * step, y * step, step);
                argb[y * 16 + x] = pixel;
                if ((pixel >>> 24) >= 24) {
                    float light = light(pixel);
                    darkest = Math.min(darkest, light);
                    brightest = Math.max(brightest, light);
                }
            }
        }
        float range = brightest - darkest;
        int offset = item ? 1 : 0;
        NativeImage image = item ? new NativeImage(CANVAS, CANVAS, true) : new NativeImage(16, 16, true);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = argb[y * 16 + x];
                int alpha = pixel >>> 24;
                if (alpha < 24) {
                    continue;
                }
                float light = light(pixel);
                if (range > 0.05F) {
                    light = STRETCH_FLOOR + (1.0F - STRETCH_FLOOR) * (light - darkest) / range;
                }
                int mapped = CrystalTheme.lerp(0xFF000000 | (pixel & 0xFFFFFF), 0xFF000000 | ramp(light), MAP_SHARE);
                // The edges facing the light, up and to the left, catch a pale rim.
                if (item && (empty(argb, x - 1, y) || empty(argb, x, y - 1))) {
                    mapped = CrystalTheme.lerp(mapped, 0xFF000000 | RIM, 0.35F);
                }
                image.setPixel(x + offset, y + offset, (alpha << 24) | (mapped & 0xFFFFFF));
            }
        }
        if (!item) {
            return image;
        }
        // The outline: every empty canvas pixel that touches the sprite.
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                if (!empty(argb, x - 1, y - 1)) {
                    continue;
                }
                if (!empty(argb, x - 2, y - 1) || !empty(argb, x, y - 1) || !empty(argb, x - 1, y - 2) || !empty(argb, x - 1, y)) {
                    image.setPixel(x, y, OUTLINE);
                }
            }
        }
        return image;
    }

    /** One pixel of the 16 from a {@code step}-times larger sprite: the average of its block, weighted by opacity. */
    private static int sample(NativeImage source, int x0, int y0, int step) {
        if (step == 1) {
            return source.getPixel(x0, y0);
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int y = y0; y < y0 + step; y++) {
            for (int x = x0; x < x0 + step; x++) {
                int pixel = source.getPixel(x, y);
                int alpha = pixel >>> 24;
                red += (long) ((pixel >> 16) & 255) * alpha;
                green += (long) ((pixel >> 8) & 255) * alpha;
                blue += (long) (pixel & 255) * alpha;
                weight += alpha;
            }
        }
        if (weight == 0) {
            return 0;
        }
        int alpha = (int) (weight / (step * step));
        return alpha << 24 | (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    private static float light(int pixel) {
        return (0.2126F * ((pixel >> 16) & 255) + 0.7152F * ((pixel >> 8) & 255) + 0.0722F * (pixel & 255)) / 255.0F;
    }

    private static boolean empty(int[] argb, int x, int y) {
        return x < 0 || y < 0 || x >= 16 || y >= 16 || (argb[y * 16 + x] >>> 24) < 24;
    }

    /** The palette at {@code light}, from 0 (shadow) to 1 (bone). */
    private static int ramp(float light) {
        float scaled = Math.max(0.0F, Math.min(1.0F, light)) * (PALETTE.length - 1);
        int index = Math.min(PALETTE.length - 2, (int) scaled);
        return CrystalTheme.lerp(0xFF000000 | PALETTE[index], 0xFF000000 | PALETTE[index + 1], scaled - index) & 0xFFFFFF;
    }

    /** An item's art, its 16 pixels {@code size} wide around a centre, turned by {@code angle}. */
    public static void drawItem(GuiGraphicsExtractor graphics, Art art, float centerX, float centerY, float size, float angle, int color) {
        if (art.canvas()) {
            float canvas = size * CANVAS / 16.0F;
            HubDraw.texture(graphics, art.id(), CANVAS, CANVAS, centerX, centerY, canvas, canvas, angle, color);
        } else {
            HubDraw.texture(graphics, art.id(), art.width(), art.height(), centerX, centerY, size, size, angle, color);
        }
    }

    /** A block as a small cube, {@code edge} pixels to a side, its top and sides from the book's art. */
    public static void drawCube(GuiGraphicsExtractor graphics, Art top, Art side, float centerX, float centerY, float edge, float alpha, int tint) {
        HubDraw.isoBlock(graphics, top.id(), side.id(), centerX, centerY, edge, -1, alpha, tint);
    }

    /**
     * Draws {@code sprite} into a square of {@code size} pixels at (x, y): an item turned by {@code
     * angle}, a block as a cube.
     */
    public static void draw(GuiGraphicsExtractor graphics, Sprite sprite, float x, float y, float size, float angle, int color) {
        Art art = sprite.art();
        if (sprite.block()) {
            drawCube(graphics, art, art, x + size / 2.0F, y + size / 2.0F, size * 0.5F, ((color >>> 24) & 255) / 255.0F, color | 0xFF000000);
        } else {
            drawItem(graphics, art, x + size / 2.0F, y + size / 2.0F, size * 16.0F / CANVAS, angle, color);
        }
    }

    /** A block's face, flat, filling a square of {@code size} pixels at (x, y): a tile of a floor. */
    public static void tile(GuiGraphicsExtractor graphics, Sprite sprite, float x, float y, float size, int color) {
        HubDraw.icon(graphics, texture(sprite), x + size / 2.0F, y + size / 2.0F, size, 0.0F, color);
    }

    /**
     * A live icon for a section: the sprite bobbing on its own beat, with a little life of its own,
     * {@code phase} seconds out of step with the others.
     */
    public static HubButton.Icon icon(Sprite sprite, float phase) {
        boolean lit = sprite == Sprite.GLOWSTONE || sprite == Sprite.AMETHYST_SHARD || sprite == Sprite.END_CRYSTAL || sprite == Sprite.NETHER_STAR;
        int light = sprite == Sprite.GLOWSTONE ? 0xFFE7B54A : 0xFFB47CF2;
        return (graphics, x, y, size, color) -> {
            double seconds = System.nanoTime() / 1_000_000_000.0D + phase;
            float bob = (float) Math.sin(seconds * 2.6D) * Math.max(0.5F, size / 14.0F);
            float angle = switch (sprite) {
                case CLOCK, COMPARATOR -> 0.0F;
                case NETHER_STAR -> (float) (seconds * 0.8D % (Math.PI * 2.0D));
                case NETHERITE_SWORD -> (float) Math.sin(seconds * 3.0D) * 0.12F;
                default -> (float) Math.sin(seconds * 1.7D) * 0.06F;
            };
            if (lit) {
                // A breath of light from behind it.
                float strength = ((color >>> 24) & 255) / 255.0F * 0.3F * HubMotion.breathe(seconds, 2.2D);
                HubDraw.glowDisc(graphics, Math.round(x + size / 2.0F), Math.round(y + size / 2.0F + bob), Math.round(size * 0.75F), light,
                        strength);
            }
            draw(graphics, sprite, x, y + bob, size, angle, color);
        };
    }
}
