package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A packet of the server parked behind the practice world is handled by Minecraft's own code, on
 * the parked server's world. The packet itself is untouched and handled in its turn; the window in
 * the corner reads it first, and keeps the titles and the action bar off the practice world's HUD.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PracticePacketMixin {
    @Shadow
    @Final
    private PacketListener listener;

    @Shadow
    @Final
    private Packet<?> packet;

    @WrapMethod(method = "handle")
    private void crystalTweaks$onTheServersWorld(Operation<Void> original) {
        if (PracticeHost.ownsListener(this.listener)) {
            PracticeHost.asServer(() -> PracticeHost.serverPacket(this.packet, () -> original.call()));
        } else {
            original.call();
        }
    }
}
