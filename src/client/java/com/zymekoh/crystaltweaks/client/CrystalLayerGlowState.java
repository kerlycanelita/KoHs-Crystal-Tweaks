package com.zymekoh.crystaltweaks.client;

import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;

/** One of the extra passes that draw a crystal's model again as light; see {@link CrystalLayerGlow}. */
public final class CrystalLayerGlowState extends EndCrystalRenderState {
    /** Each layer shines in its own colour; otherwise every layer takes the glow colour, as the aura does. */
    public final boolean ownColours;

    CrystalLayerGlowState(boolean ownColours) {
        this.ownColours = ownColours;
    }
}
