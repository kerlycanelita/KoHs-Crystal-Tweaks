package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerOverrideMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance kct$replaceCrystalExplosionSound(SoundInstance original) {
        return CrystalSoundManager.replaceCrystalExplosion(original);
    }

    @Inject(
            method = "apply(Lnet/minecraft/client/sound/SoundManager$SoundList;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;reloadSounds()V"))
    private void kct$applyRuntimeSoundOverrides(CallbackInfo ci) {
        CrystalSoundManager.onSoundManagerApply((SoundManager) (Object) this);
    }
}
