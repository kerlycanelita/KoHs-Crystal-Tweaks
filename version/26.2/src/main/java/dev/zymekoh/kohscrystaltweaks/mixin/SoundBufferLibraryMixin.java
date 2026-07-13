package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
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
    private void kct$loadRuntimeStatic(
            Identifier id,
            CallbackInfoReturnable<CompletableFuture<SoundBuffer>> callback) {
        if (!CrystalSoundManager.isRuntimeLocation(id)) {
            return;
        }

        callback.setReturnValue(this.cache.computeIfAbsent(
                id,
                key -> CompletableFuture.completedFuture(CrystalSoundManager.createStaticSoundFor(key))));
    }
}
