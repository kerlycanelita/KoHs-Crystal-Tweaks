package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmark;
import com.zymekoh.crystaltweaks.core.InstantExplosion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the sound and the burst of a server explosion this client already played at the hit (see
 * {@link InstantExplosion}). The knockback, the block debris and everything else in the packet run
 * as Vanilla runs them; the packet itself is never changed.
 */
@Mixin(ClientPacketListener.class)
public abstract class InstantExplosionMixin {
    @Unique
    private boolean crystalTweaks$echo;

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void crystalTweaks$matchPlayedExplosion(ClientboundExplodePacket packet, CallbackInfo callback) {
        // The handler runs twice, first on the network thread, which only reschedules it.
        if (!Minecraft.getInstance().isSameThread()) {
            this.crystalTweaks$echo = false;
            return;
        }
        this.crystalTweaks$echo = InstantExplosion.alreadyPlayed(packet.center().x, packet.center().y, packet.center().z);
        if (!this.crystalTweaks$echo) {
            // Heard now, as Vanilla plays it: what the benchmark compares the instant explosion against.
            CrystalBenchmark.onExplosionHeard(packet.center().x, packet.center().y, packet.center().z);
        }
    }

    @WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private boolean crystalTweaks$skipPlayedSound(ClientLevel level, double x, double y, double z, SoundEvent sound,
            SoundSource source, float volume, float pitch, boolean distanceDelay) {
        return !this.crystalTweaks$echo;
    }

    @WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean crystalTweaks$skipPlayedBurst(ClientLevel level, ParticleOptions particle, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        return !this.crystalTweaks$echo;
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void crystalTweaks$forgetPlayedExplosion(ClientboundExplodePacket packet, CallbackInfo callback) {
        this.crystalTweaks$echo = false;
    }
}
