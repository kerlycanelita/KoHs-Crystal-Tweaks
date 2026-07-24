package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Maintains a short, particle-only placement preview after vanilla accepts a
 * crystal use. No entity is added to the level, so the preview cannot be
 * targeted, attacked, interacted with, or referenced by a packet.
 */
public final class CrystalPredictor {
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    private static final Map<Long, Integer> PREVIEWS = new HashMap<>();

    private static boolean enabled;
    private static int clientTick;

    private CrystalPredictor() {
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

    public static int currentTick() {
        return clientTick;
    }

    /**
     * Compatibility predicate for other local visual systems. Particle
     * previews are never entities, so this is intentionally always false.
     */
    public static boolean isLocalCrystalEntity(Entity entity) {
        return false;
    }

    public static void reset() {
        PREVIEWS.clear();
        clientTick = 0;
    }

    public static void clientTick() {
        if (!isEnabled() || MINECRAFT.level == null) {
            PREVIEWS.clear();
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
                emitPreviewParticle(BlockPos.of(entry.getKey()));
            }
        }
    }

    public static void onSuccessfulCrystalUse(BlockHitResult hit) {
        ClientLevel level = MINECRAFT.level;
        if (!isEnabled() || level == null || hit == null) {
            return;
        }

        BlockPos base = hit.getBlockPos();
        if (!level.getBlockState(base).is(Blocks.OBSIDIAN)
                && !level.getBlockState(base).is(Blocks.BEDROCK)) {
            return;
        }

        BlockPos previewPos = base.above();
        if (!level.getBlockState(previewPos).isAir()) {
            return;
        }

        int timeout = Math.max(2, Math.min(20, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks));
        PREVIEWS.put(previewPos.asLong(), clientTick + timeout);
        emitPreviewParticle(previewPos);
    }

    public static void onEntityLoaded(Entity entity) {
        if (entity instanceof EndCrystal) {
            PREVIEWS.remove(entity.blockPosition().asLong());
        }
    }

    public static void clearAll() {
        PREVIEWS.clear();
    }

    private static void emitPreviewParticle(BlockPos pos) {
        ClientLevel level = MINECRAFT.level;
        if (level == null) {
            return;
        }
        level.addParticle(
                ParticleTypes.END_ROD,
                pos.getX() + 0.5D,
                pos.getY() + 0.75D,
                pos.getZ() + 0.5D,
                0.0D,
                0.01D,
                0.0D);
    }
}
