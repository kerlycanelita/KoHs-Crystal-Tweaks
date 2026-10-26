package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeGuiAccess;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A screen the parked server opens while the player practises waits for the player's return
 * instead of covering the practice world (26.2 and 26.3, where the GUI owns the screen).
 */
@Mixin(Gui.class)
public abstract class PracticeScreenMixin implements PracticeGuiAccess {
    @Shadow
    private Screen screen;

    @Override
    public Screen crystalTweaks$screen() {
        return this.screen;
    }

    @Override
    public void crystalTweaks$setScreen(Screen screen) {
        this.screen = screen;
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$keepForServer(Screen screen, CallbackInfo callback) {
        if (PracticeHost.serverSetsScreen(screen)) {
            callback.cancel();
        }
    }
}
