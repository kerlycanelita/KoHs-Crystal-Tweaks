package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$loadRuntimeSound(
            Identifier id,
            CallbackInfoReturnable<CompletableFuture<SoundBuffer>> callback
    ) {
        if (CrystalSoundManager.isRuntimeLocation(id)) {
            callback.setReturnValue(this.cache.computeIfAbsent(
                    id,
                    ignored -> CompletableFuture.completedFuture(CrystalSoundManager.createStaticSoundFor(id))));
        }
    }
}
