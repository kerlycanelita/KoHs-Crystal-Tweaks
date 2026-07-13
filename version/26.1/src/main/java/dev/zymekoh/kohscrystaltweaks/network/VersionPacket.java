package dev.zymekoh.kohscrystaltweaks.network;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VersionPacket(int major, int minor, int patch, boolean snapshot) implements CustomPacketPayload {
    private static final Pattern SEMVER_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
    public static final Type<VersionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("marlowcrystal", "version"));
    public static final StreamCodec<FriendlyByteBuf, VersionPacket> CODEC = new StreamCodec<>() {
        @Override
        public VersionPacket decode(FriendlyByteBuf buffer) {
            return new VersionPacket(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, VersionPacket packet) {
            buffer.writeVarInt(packet.major());
            buffer.writeVarInt(packet.minor());
            buffer.writeVarInt(packet.patch());
            buffer.writeBoolean(packet.snapshot());
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
