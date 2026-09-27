package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.benchmark.BenchmarkHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timestamps for the optimizer benchmark. Reads the packets and returns: nothing is cancelled,
 * changed or delayed, and while no benchmark is running each hook is a single volatile read.
 *
 * <p>Each handler runs twice in Vanilla, first on the network thread, which reschedules it, then on
 * the game thread. At HEAD both passes are seen, which is how the benchmark tells when an answer
 * arrived from when the game acted on it.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class BenchmarkPacketMixin {
    @Inject(method = "handleBundlePacket", at = @At("HEAD"))
    private void crystalTweaks$benchmarkBundle(ClientboundBundlePacket packet, CallbackInfo callback) {
        BenchmarkHooks.bundle(packet);
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void crystalTweaks$benchmarkSpawn(ClientboundAddEntityPacket packet, CallbackInfo callback) {
        BenchmarkHooks.spawn(packet);
    }

    @Inject(method = "handleBlockChangedAck", at = @At("HEAD"))
    private void crystalTweaks$benchmarkAck(ClientboundBlockChangedAckPacket packet, CallbackInfo callback) {
        BenchmarkHooks.acknowledged(packet);
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void crystalTweaks$benchmarkExplosion(ClientboundExplodePacket packet, CallbackInfo callback) {
        BenchmarkHooks.explosion(packet);
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void crystalTweaks$benchmarkRemoval(ClientboundRemoveEntitiesPacket packet, CallbackInfo callback) {
        BenchmarkHooks.removal(packet);
    }
}
