package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A screen the parked server opens while the player practises waits for the player's return
 * instead of covering the practice world. 26.2 moved the screen into the GUI, so 26.2 and 26.3 have
 * their own copy of this Mixin under {@code src/gui}.
 */
@Mixin(Minecraft.class)
public abstract class PracticeScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$keepForServer(Screen screen, CallbackInfo callback) {
        if (PracticeHost.serverSetsScreen(screen)) {
            callback.cancel();
        }
    }
}
