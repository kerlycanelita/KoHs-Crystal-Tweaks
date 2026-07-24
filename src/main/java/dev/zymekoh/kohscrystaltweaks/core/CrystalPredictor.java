package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Maintains a short, particle-only placement preview after vanilla accepts a crystal use.
 *
 * <p>No entity is added to the client world, so the preview cannot be targeted, attacked,
 * interacted with, or referenced by an outgoing packet.</p>
 */
public final class CrystalPredictor {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Map<Long, Integer> PREVIEWS = new HashMap<>();

    private static boolean enabled;
    private static int clientTick;

    private CrystalPredictor() {
    }

    public static int debugTick() {
        return clientTick;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            clearAll();
        }
    }

    public static boolean isEnabled() {
        return enabled && KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled;
    }

    /**
     * Particle previews are never entities, so this predicate is intentionally always false.
     */
    public static boolean isLocalCrystalEntity(Entity entity) {
        return false;
    }

    public static void reset() {
        PREVIEWS.clear();
        clientTick = 0;
        SeamlessCrystalBridge.clearAll();
    }

    public static void clientTick() {
        if (!isEnabled() || CLIENT.world == null) {
            clearAll();
            return;
        }

        clientTick++;
        Iterator<Map.Entry<Long, Integer>> iterator = PREVIEWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            if (clientTick >= entry.getValue()) {
                iterator.remove();
                continue;
            }
            if ((clientTick & 1) == 0) {
                emitPreviewParticle(BlockPos.fromLong(entry.getKey()));
            }
        }
    }

    public static void onUseBlock(BlockHitResult hit) {
        if (!isEnabled() || hit == null || CLIENT.world == null) {
            return;
        }

        BlockPos base = hit.getBlockPos();
        BlockState state = CLIENT.world.getBlockState(base);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
            return;
        }

        BlockPos previewPosition = base.up();
        if (!CLIENT.world.getBlockState(previewPosition).isAir()) {
            return;
        }

        int timeout = Math.max(2, Math.min(20, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks));
        PREVIEWS.put(previewPosition.asLong(), clientTick + timeout);
        emitPreviewParticle(previewPosition);
    }

    public static void onEntityLoaded(Entity entity) {
        if (entity instanceof EndCrystalEntity) {
            PREVIEWS.remove(entity.getBlockPos().asLong());
        }
    }

    public static void onEntityUnloaded(Entity entity) {
        // A particle preview has no entity lifecycle to reconcile.
    }

    public static void clearAll() {
        PREVIEWS.clear();
        SeamlessCrystalBridge.clearAll();
    }

    private static void emitPreviewParticle(BlockPos position) {
        if (CLIENT.world == null) {
            return;
        }
        CLIENT.world.addParticleClient(
                ParticleTypes.END_ROD,
                position.getX() + 0.5D,
                position.getY() + 0.75D,
                position.getZ() + 0.5D,
                0.0D,
                0.01D,
                0.0D);
    }
}
