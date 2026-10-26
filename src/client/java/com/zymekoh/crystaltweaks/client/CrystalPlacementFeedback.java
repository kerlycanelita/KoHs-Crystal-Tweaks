package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmark;
import com.zymekoh.crystaltweaks.client.fight.PlacementDiagnostic;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalCore;
import com.zymekoh.crystaltweaks.core.CrystalPlacementTracker;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Which crystals are the player's own, for their colours, and the round trip of each placement, for
 * the client-side crystal's windows and its ghost crystals. It observes a placement packet only after
 * Vanilla has sent it, and never changes input, targeting, inventory or networking.
 */
public final class CrystalPlacementFeedback {
    private static final CrystalPlacementTracker TRACKER = new CrystalPlacementTracker();
    private static boolean initialized;

    private CrystalPlacementFeedback() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof EndCrystal crystal && !PracticeHost.serverSide()) {
                onCrystalLoaded(crystal);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> TRACKER.cleanup(System.nanoTime()));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> resetState());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetState());
    }

    private static void resetState() {
        TRACKER.reset();
        CrystalOwnership.reset();
    }

    public static long lastLatencyMillis() {
        return TRACKER.lastLatencyMillis();
    }

    public static long averageLatencyMillis() {
        return TRACKER.averageLatencyMillis();
    }

    /** Called from the tail of Connection.send(Packet), after the Vanilla send path. */
    public static void afterVanillaPacketSent(Packet<?> packet) {
        if (!(packet instanceof ServerboundUseItemOnPacket usePacket)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> observePlacementPacket(usePacket));
            return;
        }
        observePlacementPacket(usePacket);
    }

    private static void observePlacementPacket(ServerboundUseItemOnPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        ItemStack usedStack = minecraft.player.getItemInHand(packet.getHand());
        BlockPos base = packet.getHitResult().getBlockPos();
        CrystalOwnership.observeWorld(minecraft.level);
        if (usedStack.is(Items.END_CRYSTAL)) {
            CrystalOwnership.record(base, packet.getSequence(), System.nanoTime());
            // Timed whoever handles the crystal, like the ownership colours.
            CrystalBenchmark.onPlacementSent(base, packet.getSequence());
        }

        if (!usedStack.is(Items.END_CRYSTAL)) {
            return;
        }
        long now = System.nanoTime();
        PlacementDiagnostic.placementSent(minecraft.level, base, now);
        // The round trip is timed for the client-side crystal's windows and for the latency meter. While
        // neither wants it, nothing below runs, not even the entity scan.
        if (!measuring() || !isVanillaPlacementCandidate(minecraft, base)) {
            return;
        }
        TRACKER.record(base, packet.getSequence(), now);
        GhostCrystalTracker.add(base, now);
    }

    private static boolean measuring() {
        return CrystalCore.active() || CrystalVisualConfig.latencyMeter();
    }

    /** What Vanilla's EndCrystalItem checks before it places: the server will place here too. */
    private static boolean isVanillaPlacementCandidate(Minecraft minecraft, BlockPos base) {
        if (!minecraft.level.getWorldBorder().isWithinBounds(base)) {
            return false;
        }
        BlockState baseState = minecraft.level.getBlockState(base);
        if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return false;
        }
        BlockPos above = base.above();
        if (!minecraft.level.isEmptyBlock(above)) {
            return false;
        }
        AABB placementBox = new AABB(above.getX(), above.getY(), above.getZ(),
                above.getX() + 1.0D, above.getY() + 2.0D, above.getZ() + 1.0D);
        return minecraft.level.getEntities(null, placementBox).isEmpty();
    }

    private static void onCrystalLoaded(EndCrystal crystal) {
        CrystalOwnership.loaded(crystal);
        BlockPos base = BlockPos.containing(crystal.getX(), crystal.getY() - 1.0D, crystal.getZ());
        PlacementDiagnostic.crystalLoaded(base);
        if (!measuring()) {
            return;
        }
        // The round trip this measures is the one the break prediction has to outlast, so feed it
        // through instead of discarding it.
        GhostCrystalTracker.confirm(base);
        TRACKER.confirm(base, System.nanoTime()).ifPresent(
                latencyNanos -> CrystalBreakPrediction.reportMeasuredLatency(Math.round(latencyNanos / 1_000_000.0D)));
    }
}
