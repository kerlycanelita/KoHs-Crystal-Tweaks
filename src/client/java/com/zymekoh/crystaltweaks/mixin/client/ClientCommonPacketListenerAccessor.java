package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which connection a packet listener answers for: the parked server's, or the practice world's. */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerAccessor {
    @Accessor("connection")
    Connection crystalTweaks$connection();
}
