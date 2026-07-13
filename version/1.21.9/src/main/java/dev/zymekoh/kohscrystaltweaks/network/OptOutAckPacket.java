package dev.zymekoh.kohscrystaltweaks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OptOutAckPacket() implements CustomPayload {
    public static final OptOutAckPacket INSTANCE = new OptOutAckPacket();
    public static final Id<OptOutAckPacket> ID = new Id<>(Identifier.of("marlowcrystal", "opt_out_ack"));
    public static final PacketCodec<PacketByteBuf, OptOutAckPacket> CODEC = new PacketCodec<>() {
        @Override
        public OptOutAckPacket decode(PacketByteBuf buf) {
            return INSTANCE;
        }

        @Override
        public void encode(PacketByteBuf buf, OptOutAckPacket packet) {
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
