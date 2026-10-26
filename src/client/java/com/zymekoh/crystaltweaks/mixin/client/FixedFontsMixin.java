package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.font.GlyphProvider;
import com.zymekoh.crystaltweaks.client.hub.GrimoireFonts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Tweaks' settings letters, read from the mod's own files (see {@link GrimoireFonts}). After
 * the game has built its fonts from the resource packs, these are set over whatever it built under
 * the same names, so no pack can change them. Every other font is left as the packs made it.
 */
@Mixin(FontManager.class)
public abstract class FixedFontsMixin {
    @Shadow
    @Final
    private Map<Identifier, FontSet> fontSets;

    @Shadow
    @Final
    private List<GlyphProvider> providersToClose;

    @Shadow
    private FontSet createFontSet(Identifier id, List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
        throw new AssertionError();
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void crystalTweaks$fixedFonts(CallbackInfo callback) {
        for (GrimoireFonts.Face face : GrimoireFonts.FACES) {
            GlyphProvider provider = GrimoireFonts.load(face);
            if (provider == null) {
                continue;
            }
            // Closed by the next reload, with the game's own.
            this.providersToClose.add(provider);
            FontSet set = this.createFontSet(face.id(),
                    List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
            FontSet replaced = this.fontSets.put(face.id(), set);
            if (replaced != null) {
                replaced.close();
            }
            GrimoireFonts.installed(face, set, provider);
        }
        GrimoireFonts.reloaded();
    }
}
