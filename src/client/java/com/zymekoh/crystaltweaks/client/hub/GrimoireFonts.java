package com.zymekoh.crystaltweaks.client.hub;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.mixin.client.FontAccessor;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

/**
 * The settings' own lettering: UnifrakturMaguntia for titles and buttons, Texturina, a text face cut
 * from the same blackletter but made to be read, for everything else.
 *
 * <p>Both fonts ship inside the mod, outside its assets, and are read straight from its own files,
 * never through the game's resources: a resource pack that changes Minecraft's font, or even ships
 * fonts under Crystal Tweaks' own name, does not change these. After every resource reload the
 * game's font manager is handed these font sets over whatever it built (see the FontManager mixin).
 * {@link #font()} is a {@link Font} that draws its plain text in Texturina, so a screen built on it
 * measures and draws with the same letters. Only a character neither font has falls back to the
 * game's, and only the crystal in the preview follows the packs, as a crystal in the world does.</p>
 *
 * <p>Both fonts are under the SIL Open Font License; their licences ship beside them.</p>
 */
public final class GrimoireFonts {
    public static final Identifier TEXT = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire_text");
    public static final Identifier GOTHIC = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire_gothic");
    /** The same letters rasterised finer, for titles drawn several times larger. */
    public static final Identifier GOTHIC_LARGE = Identifier.fromNamespaceAndPath("crystal_tweaks", "grimoire_gothic_large");

    private static final String TEXTURINA = "/crystal_tweaks_fixed/fonts/Texturina.ttf";
    private static final String FRAKTUR = "/crystal_tweaks_fixed/fonts/UnifrakturMaguntia-Book.ttf";

    /** One font set: its id, its file, its size in GUI pixels, how finely it is rasterised, and its nudge down. */
    public record Face(Identifier id, String file, float size, float oversample, float shiftY) {
    }

    public static final List<Face> FACES = List.of(
            new Face(TEXT, TEXTURINA, 10.0F, 4.0F, 0.0F),
            new Face(GOTHIC, FRAKTUR, 11.0F, 4.0F, 0.0F),
            new Face(GOTHIC_LARGE, FRAKTUR, 11.0F, 12.0F, 0.0F));

    private static final Style GOTHIC_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(GOTHIC));
    private static final Style GOTHIC_LARGE_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(GOTHIC_LARGE));

    /** A label in gothic letters, laid out once, and its width: drawn and measured every frame, made once. */
    private record Label(FormattedCharSequence sequence, int width) {
    }

    /** Labels by their text, one map per size so a lookup builds no key: a few hundred at most. */
    private static final Map<String, Label> GOTHIC_LABELS = new HashMap<>();
    private static final Map<String, Label> GOTHIC_LARGE_LABELS = new HashMap<>();
    private static final int CACHE_LIMIT = 512;

    private static final FontSet[] SETS = new FontSet[3];
    private static final IntSet[] SUPPORTED = {IntSets.EMPTY_SET, IntSets.EMPTY_SET, IntSets.EMPTY_SET};
    private static GlyphSource text;
    private static GlyphSource gothic;
    private static GlyphSource gothicLarge;
    private static Font.Provider game;
    private static Font font;

    private GrimoireFonts() {
    }

    /** Reads one font from the mod's own files and opens it for the font manager; null if it cannot. */
    public static GlyphProvider load(Face face) {
        ByteBuffer data = null;
        FT_Face handle = null;
        try (InputStream in = GrimoireFonts.class.getResourceAsStream(face.file())) {
            if (in == null) {
                CrystalTweaksClient.LOGGER.warn("Crystal Tweaks could not find its font {}", face.file());
                return null;
            }
            data = TextureUtil.readResource(in);
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer pointer = stack.mallocPointer(1);
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, 0L, pointer), "Opening " + face.file());
                    handle = FT_Face.create(pointer.get());
                }
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(handle, FreeType.FT_ENCODING_UNICODE), "Unicode map of " + face.file());
                return new TrueTypeGlyphProvider(data, handle, face.size(), face.oversample(), 0.0F, face.shiftY(), "");
            }
        } catch (Exception exception) {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                if (handle != null) {
                    FreeType.FT_Done_Face(handle);
                }
            }
            if (data != null) {
                MemoryUtil.memFree(data);
            }
            CrystalTweaksClient.LOGGER.warn("Crystal Tweaks could not open its font {}", face.file(), exception);
            return null;
        }
    }

    /** The font manager built one of the sets: kept, with the characters it has. */
    public static void installed(Face face, FontSet set, GlyphProvider provider) {
        int index = FACES.indexOf(face);
        SETS[index] = set;
        SUPPORTED[index] = provider.getSupportedGlyphs();
    }

    /** The fonts were rebuilt: labels laid out with the old ones go, and the sources follow the new sets. */
    public static void reloaded() {
        GOTHIC_LABELS.clear();
        GOTHIC_LARGE_LABELS.clear();
        GlyphSource fallback = new GlyphSource() {
            @Override
            public BakedGlyph getGlyph(int codepoint) {
                return game().glyphs(FontDescription.DEFAULT).getGlyph(codepoint);
            }

            @Override
            public BakedGlyph getRandomGlyph(RandomSource random, int width) {
                return game().glyphs(FontDescription.DEFAULT).getRandomGlyph(random, width);
            }
        };
        GlyphSource words = layered(0, fallback);
        gothic = layered(1, words);
        gothicLarge = layered(2, words);
        // Texturina's figures are old style, its 0 a small o: the text takes the gothic letters' upright ones.
        GlyphSource figures = gothic;
        text = new GlyphSource() {
            @Override
            public BakedGlyph getGlyph(int codepoint) {
                return codepoint >= '0' && codepoint <= '9' ? figures.getGlyph(codepoint) : words.getGlyph(codepoint);
            }

            @Override
            public BakedGlyph getRandomGlyph(RandomSource random, int width) {
                return words.getRandomGlyph(random, width);
            }
        };
    }

    /** The set's own letters, and {@code rest} for any it lacks. */
    private static GlyphSource layered(int index, GlyphSource rest) {
        FontSet set = SETS[index];
        if (set == null) {
            return rest;
        }
        GlyphSource own = set.source(false);
        IntSet supported = SUPPORTED[index];
        return new GlyphSource() {
            @Override
            public BakedGlyph getGlyph(int codepoint) {
                return supported.contains(codepoint) ? own.getGlyph(codepoint) : rest.getGlyph(codepoint);
            }

            @Override
            public BakedGlyph getRandomGlyph(RandomSource random, int width) {
                return own.getRandomGlyph(random, width);
            }
        };
    }

    private static Font.Provider game() {
        if (game == null) {
            game = ((FontAccessor) Minecraft.getInstance().font).crystalTweaks$provider();
        }
        return game;
    }

    /** The font the settings draw with: Texturina for plain text, the gothic letters where a style asks. */
    public static Font font() {
        if (font == null) {
            font = new Font(new Font.Provider() {
                @Override
                public GlyphSource glyphs(FontDescription description) {
                    if (text != null && description instanceof FontDescription.Resource resource) {
                        Identifier id = resource.id();
                        if (description.equals(FontDescription.DEFAULT) || id.equals(TEXT)) {
                            return text;
                        }
                        if (id.equals(GOTHIC)) {
                            return gothic;
                        }
                        if (id.equals(GOTHIC_LARGE)) {
                            return gothicLarge;
                        }
                    }
                    return game().glyphs(description);
                }

                @Override
                public EffectGlyph effect() {
                    return game().effect();
                }
            });
        }
        return font;
    }

    public static FormattedCharSequence gothic(String value) {
        return label(GOTHIC_LABELS, value, GOTHIC_STYLE).sequence();
    }

    public static FormattedCharSequence gothicLarge(String value) {
        return label(GOTHIC_LARGE_LABELS, value, GOTHIC_LARGE_STYLE).sequence();
    }

    /** The width of {@code value} in the gothic letters, at their own size. */
    public static int gothicWidth(String value) {
        return label(GOTHIC_LABELS, value, GOTHIC_STYLE).width();
    }

    public static int gothicLargeWidth(String value) {
        return label(GOTHIC_LARGE_LABELS, value, GOTHIC_LARGE_STYLE).width();
    }

    private static Label label(Map<String, Label> labels, String value, Style style) {
        Label label = labels.get(value);
        if (label == null) {
            if (labels.size() >= CACHE_LIMIT) {
                labels.clear();
            }
            FormattedCharSequence sequence = Component.literal(value).withStyle(style).getVisualOrderText();
            label = new Label(sequence, font().width(sequence));
            labels.put(value, label);
        }
        return label;
    }
}
