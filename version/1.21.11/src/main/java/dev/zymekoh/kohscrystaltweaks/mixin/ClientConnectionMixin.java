package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.marlow.InteractHandler;
import dev.zymekoh.kohscrystaltweaks.marlow.MarlowOptimizerCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Unique
    private static final InteractHandler KCT$INTERACT_HANDLER = new InteractHandler(MinecraftClient.getInstance());

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void kct$onPacketSend(Packet<?> packet, CallbackInfo ci) {
        if (!CrystalPredictor.isEnabled() || MarlowOptimizerCompat.isOptedOut()) {
            return;
        }
        if (packet instanceof PlayerInteractEntityC2SPacket interactionPacket) {
            KCT$INTERACT_HANDLER.handle(interactionPacket);
        }
    }
}
