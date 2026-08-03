package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerOverrideMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance crystalTweaks$replaceCrystalExplosion(SoundInstance original) {
        return CrystalSoundManager.replaceCrystalExplosion(original);
    }

    @Inject(
            method = "apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;"
                    + "Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;reload()V")
    )
    private void crystalTweaks$applyRuntimeSound(CallbackInfo callback) {
        CrystalSoundManager.onSoundManagerApply((SoundManager) (Object) this);
    }
}
