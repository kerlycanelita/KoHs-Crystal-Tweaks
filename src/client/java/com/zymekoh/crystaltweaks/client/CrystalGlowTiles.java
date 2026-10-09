package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The crystal's light, baked from its own texture: the light of every pixel of a face, spread
 * softly round it and kept in a tile that the face wears as a sheet a little wider than itself. The
 * glow follows the frames' pattern and the core's marks, and passes each box's outline softly.
 *
 * <p>A tile is the face with a margin all round, and the light of a pixel is gone exactly that far
 * from it: a sheet never ends in light. The tiles hold only how much light there is; its colour is
 * the layer's, given when the sheet is drawn. They are baked again when the resource packs change,
 * so the glow follows whatever texture the crystal has.</p>
 *
 * <p>The sheets are drawn with Vanilla's beacon beam material: a texture times a colour, blended
 * over what is behind, tested against depth and writing none. The glow is laid over the scene and
 * not added to it, so it keeps its colour in daylight and never burns out to white.</p>
 */
final class CrystalGlowTiles {
    /** A face's sheet reaches this far past each of its edges, in faces: the frames', and the core's wider one. */
    static final float GLASS_BORDER = 0.25F;
    static final float CORE_BORDER = 0.5F;
    /** Texels across a face in its tile. */
    private static final int FACE = 80;
    private static final int COLUMNS = 6;
    private static final Kind GLASS = new Kind(GLASS_BORDER, 0, new float[] {0.045F, 0.11F}, new float[] {0.6F, 0.4F},
            4.5F, 0.92F, 0.5F);
    private static final Kind CORE = new Kind(CORE_BORDER, GLASS.tile, new float[] {0.09F, 0.24F}, new float[] {0.5F, 0.5F},
            3.2F, 0.9F, 0.3F);
    /** The core's own light: a round one, bright in the middle and gone at its rim. */
    private static final int ORB = 96;
    private static final int ORB_TOP = GLASS.tile + CORE.tile;
    private static final int WIDTH = COLUMNS * Math.max(GLASS.tile, CORE.tile);
    private static final int HEIGHT = ORB_TOP + ORB;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "glow_tiles");

    private static DynamicTexture texture;
    private static RenderType material;
    private static boolean listening;
    /** Cleared when the resource packs reload: the crystal's texture may be another one. */
    private static volatile boolean baked;
    private static boolean failed;

    private CrystalGlowTiles() {
    }

    /**
     * One kind of face and how its light spreads.
     *
     * @param border how far past the face its sheet reaches, in faces
     * @param top    the row of the atlas its tiles start at
     * @param reach  a pixel's light as it spreads, in faces: tight round the pixel, and a soft skirt
     * @param share  how much of the light goes to each reach
     * @param gain   how fast the glow fills up with light
     * @param cover  the most it covers what is under it
     * @param onLight how much of that is left over a lit pixel itself, whose texture should still show
     */
    private record Kind(float border, int top, float[] reach, float[] share, float gain, float cover, float onLight,
            int margin, int tile) {
        Kind(float border, int top, float[] reach, float[] share, float gain, float cover, float onLight) {
            this(border, top, reach, share, gain, cover, onLight, Math.round(FACE * border), FACE + 2 * Math.round(FACE * border));
        }
    }

    /** The sheets' material, with the tiles baked from the crystal's texture as it is now; null if it cannot be read. */
    static RenderType material(CrystalShape shape) {
        if (!baked && !bake(shape)) {
            return null;
        }
        return material;
    }

    /** Where on the atlas a point of a face's sheet is: {@code at} runs from minus the border to one plus it. */
    static float u(boolean core, int face, float at) {
        Kind kind = core ? CORE : GLASS;
        return (face * kind.tile + 0.5F + (at + kind.border) / (1.0F + 2.0F * kind.border) * (kind.tile - 1)) / WIDTH;
    }

    static float v(boolean core, float at) {
        Kind kind = core ? CORE : GLASS;
        return (kind.top + 0.5F + (at + kind.border) / (1.0F + 2.0F * kind.border) * (kind.tile - 1)) / HEIGHT;
    }

    /** Where on the atlas a point of the core's round light is: {@code at} runs from zero to one across it. */
    static float orbU(float at) {
        return (0.5F + at * (ORB - 1)) / WIDTH;
    }

    static float orbV(float at) {
        return (ORB_TOP + 0.5F + at * (ORB - 1)) / HEIGHT;
    }

    private static boolean bake(CrystalShape shape) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!listening && minecraft.getResourceManager() instanceof ReloadableResourceManager manager) {
            manager.registerReloadListener((ResourceManagerReloadListener) ignored -> {
                baked = false;
                failed = false;
            });
            listening = true;
        }
        if (failed) {
            return false;
        }
        Optional<Resource> resource = minecraft.getResourceManager().getResource(CrystalLight.TEXTURE);
        if (resource.isEmpty()) {
            failed = true;
            return false;
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            if (texture == null) {
                texture = new DynamicTexture(() -> "Crystal Tweaks glow", WIDTH, HEIGHT, true);
                minecraft.getTextureManager().register(ID, texture);
                material = RenderTypes.beaconBeam(ID, true);
            }
            NativeImage pixels = texture.getPixels();
            if (pixels == null) {
                failed = true;
                return false;
            }
            for (int face = 0; face < COLUMNS; face++) {
                bake(pixels, image, shape.glass[face], GLASS, face, false);
                bake(pixels, image, shape.core[face], CORE, face, true);
            }
            for (int j = 0; j < ORB; j++) {
                for (int i = 0; i < ORB; i++) {
                    float x = (i + 0.5F) / ORB * 2.0F - 1.0F;
                    float y = (j + 0.5F) / ORB * 2.0F - 1.0F;
                    float inside = Math.max(0.0F, 1.0F - (x * x + y * y));
                    pixels.setPixel(i, ORB_TOP + j, Math.round(255.0F * inside * inside * inside) << 24 | 0xFFFFFF);
                }
            }
            texture.upload();
            baked = true;
            return true;
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Could not read the crystal's texture for its glow", exception);
            failed = true;
            return false;
        }
    }

    /**
     * Spreads the light of one face's pixels over its tile. Each gives as much as it is bright; the
     * light is blurred along the rows and then down the columns, once for each reach.
     */
    private static void bake(NativeImage pixels, NativeImage image, CrystalShape.Face face, Kind kind, int column,
            boolean core) {
        int left = Math.round(face.u0() * image.getWidth());
        int top = Math.round(face.v0() * image.getHeight());
        int width = Math.max(1, Math.round(face.u1() * image.getWidth()) - left);
        int height = Math.max(1, Math.round(face.v1() * image.getHeight()) - top);
        float[] source = new float[width * height];
        boolean[] lit = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colour = image.getPixel(Math.min(image.getWidth() - 1, left + x), Math.min(image.getHeight() - 1, top + y));
                float alpha = (colour >>> 24) / 255.0F;
                if (alpha < 0.1F) {
                    continue;
                }
                float bright = Math.max(colour >> 16 & 255, Math.max(colour >> 8 & 255, colour & 255)) / 255.0F;
                // A frame's pixels all shine; of the core's solid face, the brighter marks shine most.
                source[y * width + x] = alpha * (core ? 0.2F + 0.8F * bright * bright : 0.35F + 0.65F * bright);
                lit[y * width + x] = true;
            }
        }
        int tile = kind.tile;
        float[] sum = new float[tile * tile];
        float[] rows = new float[tile * height];
        for (int reach = 0; reach < kind.reach.length; reach++) {
            Spread across = new Spread(kind, width, kind.reach[reach]);
            Spread down = width == height ? across : new Spread(kind, height, kind.reach[reach]);
            for (int y = 0; y < height; y++) {
                for (int i = 0; i < tile; i++) {
                    float light = 0.0F;
                    for (int x = across.first[i]; x < across.end[i]; x++) {
                        light += source[y * width + x] * across.weight[i * width + x];
                    }
                    rows[y * tile + i] = light;
                }
            }
            for (int j = 0; j < tile; j++) {
                for (int i = 0; i < tile; i++) {
                    float light = 0.0F;
                    for (int y = down.first[j]; y < down.end[j]; y++) {
                        light += rows[y * tile + i] * down.weight[j * height + y];
                    }
                    sum[j * tile + i] += light * kind.share[reach];
                }
            }
        }
        for (int j = 0; j < tile; j++) {
            for (int i = 0; i < tile; i++) {
                float amount = sum[j * tile + i];
                int x = Math.floorDiv((i - kind.margin) * width, FACE);
                int y = Math.floorDiv((j - kind.margin) * height, FACE);
                boolean onLight = x >= 0 && y >= 0 && x < width && y < height && lit[y * width + x];
                int alpha = amount <= 1.0E-4F ? 0
                        : Math.round(kind.cover * (onLight ? kind.onLight : 1.0F) * (1.0F - (float) Math.exp(-kind.gain * amount)) * 255.0F);
                pixels.setPixel(column * tile + i, kind.top + j, alpha << 24 | 0xFFFFFF);
            }
        }
    }

    /**
     * How much of each pixel along one axis of a face reaches each texel along its tile: a bell
     * {@code reach} wide, brought down to nothing a border away, so the light of a pixel on the
     * face's edge is gone exactly at the tile's. A row lit from end to end adds up to one.
     */
    private static final class Spread {
        final float[] weight;
        /** Per texel, the pixels that reach it: from {@code first} up to {@code end}. */
        final int[] first;
        final int[] end;

        Spread(Kind kind, int size, float reach) {
            int tile = kind.tile;
            this.weight = new float[tile * size];
            this.first = new int[tile];
            this.end = new int[tile];
            double whole = 0.0D;
            for (int step = -500; step < 500; step++) {
                whole += bell((step + 0.5F) * kind.border / 500.0F, reach, kind.border) * kind.border / 500.0D;
            }
            for (int i = 0; i < tile; i++) {
                float at = (i + 0.5F) / FACE - kind.border;
                this.first[i] = size;
                for (int x = 0; x < size; x++) {
                    float value = bell(at - (x + 0.5F) / size, reach, kind.border);
                    if (value <= 0.0F) {
                        continue;
                    }
                    this.weight[i * size + x] = (float) (value / (whole * size));
                    this.first[i] = Math.min(this.first[i], x);
                    this.end[i] = x + 1;
                }
            }
        }

        private static float bell(float distance, float reach, float border) {
            float part = distance / border;
            if (part <= -1.0F || part >= 1.0F) {
                return 0.0F;
            }
            float window = 1.0F - part * part;
            return (float) Math.exp(-distance * distance / (2.0F * reach * reach)) * window * window;
        }
    }
}
