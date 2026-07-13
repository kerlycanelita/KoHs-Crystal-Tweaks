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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

        PayloadTypeRegistry.clientboundConfiguration().register(OptOutPacket.TYPE, OptOutPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OptOutPacket.TYPE, OptOutPacket.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(OptOutAckPacket.TYPE, OptOutAckPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OptOutAckPacket.TYPE, OptOutAckPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VersionPacket.TYPE, VersionPacket.CODEC);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!client.hasSingleplayerServer()) {
                sender.sendPacket(VERSION_PACKET);
            }
            String serverKey = currentServerKey(client);
            OPT_OUT_CACHE.setOptedOut(OPT_OUT_CACHE.isServerOptedOut(serverKey));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OPT_OUT_CACHE.clearCurrentSession());
        ClientPlayNetworking.registerGlobalReceiver(OptOutPacket.TYPE,
                (payload, context) -> handleOptOut(context.client(), context.responseSender()));
        ClientConfigurationNetworking.registerGlobalReceiver(OptOutPacket.TYPE, (payload, context) ->
                context.client().execute(() -> handleOptOut(context.client(), context.responseSender())));
    }

    public static boolean isOptedOut() {
        return OPT_OUT_CACHE.isOptedOut();
    }

    private static void handleOptOut(Minecraft client, PacketSender responseSender) {
        String serverKey = currentServerKey(client);
        if (serverKey != null) {
            OPT_OUT_CACHE.markOptedOut(serverKey);
        } else {
            OPT_OUT_CACHE.setOptedOut(true);
        }

        responseSender.sendPacket(OptOutAckPacket.INSTANCE);
        scheduleDisabledMessage(client, serverKey);
    }

    private static void scheduleDisabledMessage(Minecraft client, String serverKey) {
        if (OPT_OUT_CACHE.hasNotified(serverKey)) {
            return;
        }

        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> client.execute(() -> {
            if (client.player == null || OPT_OUT_CACHE.hasNotified(serverKey)) {
                return;
            }

            OPT_OUT_CACHE.markNotified(serverKey);
            client.player.sendSystemMessage(Component.empty()
                    .append(Component.literal("[KoHs Crystal Tweaks] ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("Crystal optimizer disabled on this server.")
                            .withStyle(ChatFormatting.RED)));
        }));
    }

    private static String currentServerKey(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            return null;
        }
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        }
        if (client.getConnection() != null) {
            return String.valueOf(client.getConnection().getConnection().getRemoteAddress());
        }
        return null;
    }
}
