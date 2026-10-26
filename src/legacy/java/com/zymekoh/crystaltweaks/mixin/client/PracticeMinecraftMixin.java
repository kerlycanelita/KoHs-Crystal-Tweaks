package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11: Escape in a practice world opens the practice panel; see the 26.x copy for the rest. */
@Mixin(Minecraft.class)
public abstract class PracticeMinecraftMixin {
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$practicePanel(boolean suppressPauseMenu, CallbackInfo callback) {
        if (PracticeHost.openPanel()) {
            callback.cancel();
        }
    }
}
