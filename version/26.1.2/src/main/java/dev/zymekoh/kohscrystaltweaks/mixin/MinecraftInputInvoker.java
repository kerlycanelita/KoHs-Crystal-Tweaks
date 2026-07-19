package dev.zymekoh.kohscrystaltweaks.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftInputInvoker {
    @Invoker("pick")
    void kct$invokePick(float partialTicks);

    @Invoker("startAttack")
    boolean kct$invokeStartAttack();

    @Invoker("startUseItem")
    void kct$invokeStartUseItem();
}
