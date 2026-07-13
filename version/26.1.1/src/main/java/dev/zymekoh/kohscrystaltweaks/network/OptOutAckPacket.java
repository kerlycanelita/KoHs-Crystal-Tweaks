package dev.zymekoh.kohscrystaltweaks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OptOutAckPacket() implements CustomPacketPayload {
    public static final OptOutAckPacket INSTANCE = new OptOutAckPacket();
    public static final Type<OptOutAckPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("marlowcrystal", "opt_out_ack"));
    public static final StreamCodec<FriendlyByteBuf, OptOutAckPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
