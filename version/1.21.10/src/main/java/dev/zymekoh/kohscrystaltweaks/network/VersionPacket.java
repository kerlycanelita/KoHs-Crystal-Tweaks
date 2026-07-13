package dev.zymekoh.kohscrystaltweaks.network;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VersionPacket(int major, int minor, int patch, boolean snapshot) implements CustomPayload {
    private static final Pattern SEMVER_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
    public static final Id<VersionPacket> ID = new Id<>(Identifier.of("marlowcrystal", "version"));
    public static final PacketCodec<PacketByteBuf, VersionPacket> CODEC = new PacketCodec<>() {
        @Override
        public VersionPacket decode(PacketByteBuf buf) {
            return new VersionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        @Override
        public void encode(PacketByteBuf buf, VersionPacket packet) {
            buf.writeVarInt(packet.major());
            buf.writeVarInt(packet.minor());
            buf.writeVarInt(packet.patch());
            buf.writeBoolean(packet.snapshot());
        }
    };

    public static VersionPacket current() {
        String friendlyVersion = FabricLoader.getInstance()
                .getModContainer(KoHsCrystalTweaks.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");

        boolean snapshot = friendlyVersion.toUpperCase(Locale.ROOT).contains("SNAPSHOT");
        Matcher matcher = SEMVER_PREFIX.matcher(friendlyVersion.trim());
        if (!matcher.matches()) {
            return new VersionPacket(0, 0, 0, snapshot);
        }

        return new VersionPacket(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                snapshot);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
