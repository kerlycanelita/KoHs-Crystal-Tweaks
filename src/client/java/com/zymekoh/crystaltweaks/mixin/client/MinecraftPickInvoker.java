package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Vanilla's own crosshair pick so a local world change can be reflected in the same tick.
 *
 * <p>{@code Minecraft.tick()} calls {@code pick(1.0F)} before it handles keybinds, so the hit result
 * the Use and Attack actions read is always the one Vanilla produced with a partial tick of
 * {@code 1.0F}. Re-running the very same method with the very same argument is therefore an exact
 * repeat of what Vanilla already did this tick, not an approximation of it.</p>
 */
@Mixin(Minecraft.class)
public interface MinecraftPickInvoker {
    @Invoker("pick")
    void crystalTweaks$pick(float partialTicks);
}
