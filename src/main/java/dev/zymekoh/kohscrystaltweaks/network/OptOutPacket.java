package dev.zymekoh.kohscrystaltweaks.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OptOutPacket() implements CustomPayload {
    public static final Id<OptOutPacket> ID = new Id<>(Identifier.of("mco"));
    public static final PacketCodec<RegistryByteBuf, OptOutPacket> CODEC = new PacketCodec<>() {
        @Override
        public OptOutPacket decode(RegistryByteBuf buf) {
            return new OptOutPacket();
        }

        @Override
        public void encode(RegistryByteBuf buf, OptOutPacket packet) {
            // Empty payload by design: this packet only signals opt-out support.
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
