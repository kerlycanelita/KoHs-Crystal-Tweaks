package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.marlow.InteractHandler;
import dev.zymekoh.kohscrystaltweaks.marlow.MarlowOptimizerCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Unique
    private static final InteractHandler KCT$INTERACT_HANDLER = new InteractHandler(Minecraft.getInstance());

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void kct$onPacketSend(Packet<?> packet, CallbackInfo ci) {
        if (!CrystalPredictor.isEnabled() || MarlowOptimizerCompat.isOptedOut()) {
            return;
        }
        if (packet instanceof ServerboundInteractPacket interactionPacket) {
            KCT$INTERACT_HANDLER.handle(interactionPacket);
        }
    }
}
