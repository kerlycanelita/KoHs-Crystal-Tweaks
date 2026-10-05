package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * RenderType's factory is package-private; every renderer passes the type itself around. An invoker
 * rather than reflection, because 1.21.11 runs under intermediary names, where looking the method up
 * by its Mojang name finds nothing.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType crystalTweaks$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
