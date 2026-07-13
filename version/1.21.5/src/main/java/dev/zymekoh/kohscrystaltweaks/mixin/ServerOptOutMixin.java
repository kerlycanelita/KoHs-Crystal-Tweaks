package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.network.OptOutPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ServerOptOutMixin {
    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void kct$sendInfoPacket(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (ClientPlayNetworking.canSend(OptOutPacket.ID)) {
            ClientPlayNetworking.send(new OptOutPacket());
        }
    }
}
