package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EndCrystalRenderer.class)
public interface LegacyEndCrystalRendererAccessor {
    @Accessor("cube")
    ModelPart crystalTweaks$getCube();

    @Accessor("glass")
    ModelPart crystalTweaks$getGlass();

    @Accessor("base")
    ModelPart crystalTweaks$getBase();

    @Accessor("RENDER_TYPE")
    static RenderType crystalTweaks$getRenderType() {
        throw new AssertionError();
    }
}
