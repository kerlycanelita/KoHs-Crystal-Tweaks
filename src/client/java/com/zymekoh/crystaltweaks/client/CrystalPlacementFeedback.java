package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.core.CrystalPlacementTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Local feedback for Vanilla placement attempts. It observes a packet only after
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

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntityLoaded(entity));
        ClientTickEvents.END_CLIENT_TICK.register(client -> TRACKER.cleanup(System.nanoTime()));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> resetState());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetState());
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

    public static void resetState() {
        TRACKER.reset();
    }

    public static long lastLatencyMillis() {
        return TRACKER.lastLatencyMillis();
    }

    public static long averageLatencyMillis() {
        return TRACKER.averageLatencyMillis();
    }

    private static void observePlacementPacket(ServerboundUseItemOnPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        ItemStack usedStack = minecraft.player.getItemInHand(packet.getHand());
        BlockPos base = packet.getHitResult().getBlockPos();
        if (!usedStack.is(Items.END_CRYSTAL) || !isVanillaPlacementCandidate(minecraft, base)) {
            return;
        }

        TRACKER.record(base, packet.getSequence(), System.nanoTime());
        emitPlacementPulse(minecraft, base);
    }

    private static boolean isVanillaPlacementCandidate(Minecraft minecraft, BlockPos base) {
        if (!minecraft.level.getWorldBorder().isWithinBounds(base)
                || (!minecraft.level.getBlockState(base).is(Blocks.OBSIDIAN)
                && !minecraft.level.getBlockState(base).is(Blocks.BEDROCK))) {
            return false;
        }

        BlockPos above = base.above();
        if (!minecraft.level.isEmptyBlock(above)) {
            return false;
        }

        AABB placementBox = new AABB(
                above.getX(),
                above.getY(),
                above.getZ(),
                above.getX() + 1.0D,
                above.getY() + 2.0D,
                above.getZ() + 1.0D);
        return minecraft.level.getEntities(null, placementBox).isEmpty();
    }

    private static void emitPlacementPulse(Minecraft minecraft, BlockPos base) {
        double centerX = base.getX() + 0.5D;
        double centerY = base.getY() + 1.08D;
        double centerZ = base.getZ() + 0.5D;

        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0D * index / 16.0D;
            double velocityX = Math.cos(angle) * 0.055D;
            double velocityZ = Math.sin(angle) * 0.055D;
            minecraft.level.addParticle(
                    index % 4 == 0 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    centerX + Math.cos(angle) * 0.34D,
                    centerY + (index % 3) * 0.18D,
                    centerZ + Math.sin(angle) * 0.34D,
                    velocityX,
                    0.025D + (index % 2) * 0.012D,
                    velocityZ);
        }
    }

    private static void onEntityLoaded(Entity entity) {
        if (!(entity instanceof EndCrystal)) {
            return;
        }

        BlockPos base = BlockPos.containing(entity.getX(), entity.getY() - 1.0D, entity.getZ());
        TRACKER.confirm(base, System.nanoTime());
    }
}
