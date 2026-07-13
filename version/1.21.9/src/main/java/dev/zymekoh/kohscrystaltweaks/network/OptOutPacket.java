package dev.zymekoh.kohscrystaltweaks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OptOutPacket() implements CustomPayload {
    public static final OptOutPacket INSTANCE = new OptOutPacket();
    public static final Id<OptOutPacket> ID = new Id<>(Identifier.of("marlowcrystal", "opt_out"));
    public static final PacketCodec<PacketByteBuf, OptOutPacket> CODEC = new PacketCodec<>() {
        @Override
        public OptOutPacket decode(PacketByteBuf buf) {
            return INSTANCE;
        }

        @Override
        public void encode(PacketByteBuf buf, OptOutPacket packet) {
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
