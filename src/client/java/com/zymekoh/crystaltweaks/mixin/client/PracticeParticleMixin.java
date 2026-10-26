package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The particle engine draws the practice world while a server is parked: the parked server's
 * particles, which would land at its coordinates in the practice world, are not made.
 */
@Mixin(ParticleEngine.class)
public abstract class PracticeParticleMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$noServerParticle(Particle particle, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$noServerParticleMade(ParticleOptions options, double x, double y, double z, double xSpeed,
            double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> callback) {
        if (PracticeHost.serverSide()) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$noServerEmitter(Entity entity, ParticleOptions options, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$noServerTimedEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo callback) {
        if (PracticeHost.serverSide()) {
            callback.cancel();
        }
    }
}
