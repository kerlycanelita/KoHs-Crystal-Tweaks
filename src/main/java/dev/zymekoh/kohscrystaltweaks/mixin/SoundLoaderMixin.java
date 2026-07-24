package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundLoader.class)
public abstract class SoundLoaderMixin {
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<StaticSound>> loadedSounds;

    @Inject(method = "loadStatic", at = @At("HEAD"), cancellable = true)
    private void kct$loadRuntimeStatic(Identifier id, CallbackInfoReturnable<CompletableFuture<StaticSound>> cir) {
        if (!CrystalSoundManager.isRuntimeLocation(id)) {
            return;
        }

        cir.setReturnValue(this.loadedSounds.computeIfAbsent(
                id,
                key -> CompletableFuture.completedFuture(CrystalSoundManager.createStaticSoundFor(key))));
    }
}
