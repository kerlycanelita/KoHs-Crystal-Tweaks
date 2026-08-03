package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.core.CrystalAttackOptimizer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class CrystalAttackPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void crystalTweaks$processCrystalAttack(Packet<?> packet, CallbackInfo callback) {
        CrystalAttackOptimizer.handleOutgoingPacket(packet);
    }
}
