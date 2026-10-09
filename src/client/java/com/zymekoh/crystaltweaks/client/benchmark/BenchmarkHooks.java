package com.zymekoh.crystaltweaks.client.benchmark;

import com.zymekoh.crystaltweaks.client.CrystalOwnership;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Where the game's events reach the benchmark. Kept apart from the collector so the packet types,
 * which change between Minecraft versions, are named in one small file.
 */
public final class BenchmarkHooks {
    private static boolean initialized;

    private BenchmarkHooks() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(CrystalBenchmark::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> CrystalBenchmark.onDisconnect());
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (CrystalBenchmark.recording() && entity instanceof EndCrystal) {
                CrystalBenchmark.onCrystalSpawn(entity.getId(), entity.getX(), entity.getY(), entity.getZ(), false);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (CrystalBenchmark.recording() && entity instanceof EndCrystal) {
                CrystalBenchmark.onCrystalUnloaded(entity.getId());
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("crystal_tweaks", "benchmark"),
                (graphics, deltaTracker) -> BenchmarkOverlay.draw(graphics));
    }

    /** Every packet this client sends, from the head of {@code Connection.send}. */
    public static void sent(Packet<?> packet) {
        if (CrystalBenchmark.recording() && packet instanceof ServerboundSetCarriedItemPacket) {
            CrystalBenchmark.onCarriedItemSent();
        }
    }

    static boolean network() {
        return !Minecraft.getInstance().isSameThread();
    }

    public static void bundle(ClientboundBundlePacket packet) {
        // Only the network pass: on the game thread each part is handled, and hooked, on its own.
        if (!CrystalBenchmark.recording() || !network()) {
            return;
        }
        for (Packet<?> part : packet.subPackets()) {
            if (part instanceof ClientboundAddEntityPacket spawn) {
                spawn(spawn);
            }
        }
    }

    public static void spawn(ClientboundAddEntityPacket packet) {
        if (CrystalBenchmark.recording() && packet.getType() == EntityType.END_CRYSTAL) {
            CrystalBenchmark.onCrystalSpawn(packet.getId(), packet.getX(), packet.getY(), packet.getZ(), network());
        }
    }

    public static void acknowledged(ClientboundBlockChangedAckPacket packet) {
        // The network pass only: that is where it arrives after the spawns the same tick sent.
        if (CrystalBenchmark.recording() && network()) {
            CrystalBenchmark.onPlacementsAcknowledged(packet.sequence());
        }
        // The game pass: in order with the crystals the game has loaded, which is what tells whose they are.
        if (!network()) {
            CrystalOwnership.acknowledged(packet.sequence());
        }
    }

    public static void explosion(ClientboundExplodePacket packet) {
        if (CrystalBenchmark.recording()) {
            CrystalBenchmark.onExplosion(packet.center(), network());
        }
    }

    public static void removal(ClientboundRemoveEntitiesPacket packet) {
        if (network()) {
            // The removal reader: a crystal the server removed stops being drawn on arrival.
            com.zymekoh.crystaltweaks.core.CrystalBreakPrediction.serverRemoving(packet.getEntityIds());
        }
        if (CrystalBenchmark.recording()) {
            CrystalBenchmark.onRemoval(packet.getEntityIds(), network());
        }
    }
}
