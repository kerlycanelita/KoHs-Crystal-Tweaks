package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The lightmap the world draws with: the live view of the parked server lights it for that world. */
@Mixin(GameRenderer.class)
public interface GameRendererPracticeAccessor {
    @Accessor("lightmap")
    Lightmap crystalTweaks$lightmap();
}
