package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A totem popping on the parked server does not fly across the practice world's screen. */
@Mixin(GameRenderer.class)
public abstract class PracticeGameRendererMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$noServerTotem(ItemStack stack, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }
}
