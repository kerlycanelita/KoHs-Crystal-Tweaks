package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalPlacementFeedback;
import com.zymekoh.crystaltweaks.client.benchmark.BenchmarkHooks;
import com.zymekoh.crystaltweaks.core.CrystalAttackOptimizer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Watches what the client sends, for the benchmark, the ownership colours and the client-side crystal,
 * which hides a crystal once Vanilla's own attack packet has been handed over. Nothing is changed.
 */
@Mixin(Connection.class)
public abstract class CrystalAttackPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void crystalTweaks$timeSentPacket(Packet<?> packet, CallbackInfo callback) {
        BenchmarkHooks.sent(packet);
        CrystalAttackOptimizer.handleOutgoingPacket(packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("TAIL"))
    private void crystalTweaks$observeVanillaPlacement(Packet<?> packet, CallbackInfo callback) {
        CrystalPlacementFeedback.afterVanillaPacketSent(packet);
    }
}
