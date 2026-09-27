package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The additive material every glow, flash and ground spill is drawn with.
 *
 * <p>On 1.21.11 and 26.1.x this is vanilla's dragon-ray material as it stands: additive, tested
 * against depth and writing none, so any number of overlapping lights add up. 26.2 and 26.3 made
 * the dragon rays write depth, and they get their own copy of this class that turns that off.</p>
 */
final class CrystalGlowMaterial {
    private CrystalGlowMaterial() {
    }

    static RenderType glow() {
        return RenderTypes.dragonRays();
    }
}
