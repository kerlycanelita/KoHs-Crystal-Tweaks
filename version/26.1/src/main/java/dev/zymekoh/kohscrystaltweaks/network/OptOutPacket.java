package dev.zymekoh.kohscrystaltweaks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OptOutPacket() implements CustomPacketPayload {
    public static final OptOutPacket INSTANCE = new OptOutPacket();
    public static final Type<OptOutPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("marlowcrystal", "opt_out"));
    public static final StreamCodec<FriendlyByteBuf, OptOutPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
