package dev.zymekoh.kohscrystaltweaks.marlow;

import dev.zymekoh.kohscrystaltweaks.network.OptOutAckPacket;
import dev.zymekoh.kohscrystaltweaks.network.OptOutPacket;
import dev.zymekoh.kohscrystaltweaks.network.VersionPacket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MarlowOptimizerCompat {
    private static final OptOutCache OPT_OUT_CACHE = new OptOutCache();
    private static final VersionPacket VERSION_PACKET = VersionPacket.current();
    private static boolean initialized;

    private MarlowOptimizerCompat() {
    }

    public static void initClient() {
        if (initialized) {
            return;
        }
        initialized = true;

        PayloadTypeRegistry.configurationS2C().register(OptOutPacket.ID, OptOutPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OptOutPacket.ID, OptOutPacket.CODEC);
        PayloadTypeRegistry.configurationC2S().register(OptOutAckPacket.ID, OptOutAckPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(OptOutAckPacket.ID, OptOutAckPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(VersionPacket.ID, VersionPacket.CODEC);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!client.isIntegratedServerRunning()) {
                sender.sendPacket(VERSION_PACKET);
            }

            String serverKey = currentServerKey(client);
            OPT_OUT_CACHE.setOptedOut(OPT_OUT_CACHE.isServerOptedOut(serverKey));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OPT_OUT_CACHE.clearCurrentSession());

        ClientPlayNetworking.registerGlobalReceiver(OptOutPacket.ID,
                (payload, context) -> handleOptOut(context.client(), context.responseSender()));

        ClientConfigurationNetworking.registerGlobalReceiver(OptOutPacket.ID, (payload, context) -> {
            context.client().execute(() -> handleOptOut(context.client(), context.responseSender()));
        });
    }

    public static boolean isOptedOut() {
        return OPT_OUT_CACHE.isOptedOut();
    }

    private static void handleOptOut(MinecraftClient client, PacketSender responseSender) {
        String serverKey = currentServerKey(client);
        if (serverKey != null) {
            OPT_OUT_CACHE.markOptedOut(serverKey);
        } else {
            OPT_OUT_CACHE.setOptedOut(true);
        }

        responseSender.sendPacket(OptOutAckPacket.INSTANCE);
        scheduleDisabledMessage(client, serverKey);
    }

    private static void scheduleDisabledMessage(MinecraftClient client, String serverKey) {
        if (OPT_OUT_CACHE.hasNotified(serverKey)) {
            return;
        }

        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> client.execute(() -> {
            if (client.player == null || OPT_OUT_CACHE.hasNotified(serverKey)) {
                return;
            }

            OPT_OUT_CACHE.markNotified(serverKey);
            client.player.sendMessage(Text.empty()
                    .append(Text.literal("[KoHs Crystal Tweaks] ").formatted(Formatting.AQUA))
                    .append(Text.literal("Crystal optimizer disabled on this server.").formatted(Formatting.RED)), false);
        }));
    }

    private static String currentServerKey(MinecraftClient client) {
        if (client.isIntegratedServerRunning()) {
            return null;
        }

        if (client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().address;
        }

        if (client.getNetworkHandler() != null) {
            return String.valueOf(client.getNetworkHandler().getConnection().getAddress());
        }

        return null;
    }
}
