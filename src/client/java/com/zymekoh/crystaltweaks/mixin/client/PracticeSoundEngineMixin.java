package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The parked server's sounds are not played over the practice world. */
@Mixin(SoundEngine.class)
public abstract class PracticeSoundEngineMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$quietServer(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        if (PracticeHost.serverSide()) {
            callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$quietServerLater(SoundInstance sound, int delay, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }

    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$quietServerLoop(TickableSoundInstance sound, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }
}
