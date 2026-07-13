package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.compat.CrystalAnchorCounterCompat;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.mixin.EntityAgeAccessor;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class CrystalPredictor {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Long2ObjectOpenHashMap<Local> LOCAL = new Long2ObjectOpenHashMap<>();

    private static boolean enabled = true;
    private static int tick;
    private static int adaptiveTimeoutTicks = 12;
    private static boolean ageResolved;
    private static Field ageField;

    private CrystalPredictor() {
    }

    public static int debugTick() {
        return tick;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            clearAll();
        }
    }

    public static boolean isEnabled() {
        return enabled && KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled;
    }

    public static void reset() {
        clearAll();
        tick = 0;
        adaptiveTimeoutTicks = Math.max(2, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks);
    }

    public static boolean isLocalCrystalEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        Local local = LOCAL.get(entity.getBlockPos().asLong());
        return local != null && local.entity == entity;
    }

    public static void onLocalCrystalAttack(Entity entity) {
        if (!(entity instanceof EndCrystalEntity)) {
            return;
        }

        BlockPos pos = entity.getBlockPos();
        Local local = LOCAL.remove(pos.asLong());
        if (local == null) {
            return;
        }

        if (local.pairedRealId == -1) {
            CrystalAnchorCounterCompat.recordCrystalBreak(entity.getId(), entity.getUuid());
        } else {
            SeamlessCrystalBridge.clear(local.pairedRealId);
        }

        if (local.entity != null && local.entity.isAlive()) {
            local.entity.discard();
        }
    }

    public static EndCrystalEntity findRealCrystalForLocal(Entity entity) {
        if (!isLocalCrystalEntity(entity)) {
            return null;
        }
        return findRealAt(entity.getBlockPos());
    }

    public static boolean queueLocalCrystalAttack(Entity entity) {
        if (!(entity instanceof EndCrystalEntity)) {
            return false;
        }

        Local local = LOCAL.get(entity.getBlockPos().asLong());
        if (local == null || local.entity != entity || !local.entity.isAlive()) {
            return false;
        }

        // A boolean deliberately deduplicates butterfly clicks. The real entity ID is
        // never guessed and no packet is sent until the server crystal is loaded.
        local.pendingAttack = true;
        return true;
    }

    public static void discardLocalCrystal(Entity entity) {
        if (entity == null) {
            return;
        }

        long key = entity.getBlockPos().asLong();
        Local local = LOCAL.get(key);
        if (local == null || local.entity != entity) {
            return;
        }
        LOCAL.remove(key);
        if (local.pairedRealId != -1) {
            SeamlessCrystalBridge.clear(local.pairedRealId);
        }
        if (local.entity.isAlive()) {
            local.entity.discard();
        }
    }

    public static HitResult raycastIgnoringLocal(float tickDelta) {
        if (MC.world == null) {
            return BlockHitResult.createMissed(Vec3d.ZERO, Direction.DOWN, BlockPos.ORIGIN);
        }

        Entity camera = MC.getCameraEntity();
        if (camera == null) {
            return BlockHitResult.createMissed(Vec3d.ZERO, Direction.DOWN, BlockPos.ORIGIN);
        }

        double reach = 4.5;
        if (MC.player != null && MC.player.getAbilities().creativeMode) {
            reach = 5.0;
        }
        Vec3d start = camera.getCameraPosVec(tickDelta);
        Vec3d rot = camera.getRotationVec(tickDelta);
        Vec3d end = start.add(rot.x * reach, rot.y * reach, rot.z * reach);

        BlockHitResult blockHit = MC.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                camera));
        double blockDist = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : blockHit.getPos().squaredDistanceTo(start);

        Box box = camera.getBoundingBox().stretch(rot.multiply(reach)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                camera,
                start,
                end,
                box,
                entity -> entity != camera
                        && entity.isAlive()
                        && !entity.isSpectator()
                        && !isLocalCrystalEntity(entity),
                reach * reach);

        if (entityHit != null) {
            double entityDist = entityHit.getPos().squaredDistanceTo(start);
            if (entityDist < blockDist) {
                return entityHit;
            }
        }

        return blockHit;
    }

    public static void clientTick() {
        if (!isEnabled()) {
            if (!LOCAL.isEmpty()) {
                clearAll();
            }
            return;
        }

        if (MC.world == null) {
            return;
        }

        tick++;
        long[] keys = LOCAL.keySet().toLongArray();
        for (long key : keys) {
            Local local = LOCAL.get(key);
            if (local == null || local.entity == null || local.entity.isRemoved() || !local.entity.isAlive()) {
                LOCAL.remove(key);
                continue;
            }

            BlockPos pos = BlockPos.fromLong(key);
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;
            local.entity.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
            local.entity.setNoGravity(false);
            local.entity.velocityDirty = true;

            if (tick < local.expiresTick) {
                continue;
            }
            if (local.pairedRealId != -1) {
                SeamlessCrystalBridge.clear(local.pairedRealId);
            }
            local.entity.discard();
            LOCAL.remove(key);
        }
    }

    public static void onUseBlock(BlockHitResult hit) {
        if (!isEnabled()) {
            return;
        }
        if (MC.player == null || MC.world == null) {
            return;
        }
        BlockPos base = resolveBaseFromHit(hit);
        if (base == null) {
            return;
        }

        BlockPos crystalPos = base.up();
        long crystalKey = crystalPos.asLong();
        if (hasAnyRealCrystal(crystalPos)) {
            return;
        }
        Local existing = LOCAL.get(crystalKey);
        if (existing != null) {
            if (existing.pendingAttack) {
                return;
            }
            if (existing.pairedRealId != -1) {
                return;
            }

            // Replacing stale unmatched predictions keeps manual repeat-places feeling snappy
            // without changing packet timing or automating any interaction.
            if (tick - existing.createdTick >= 2) {
                spawnLocal(crystalPos);
            } else {
                existing.expiresTick = Math.min(existing.expiresTick, tick + 2);
            }
            return;
        }

        spawnLocal(crystalPos);
    }

    public static void onEntityLoaded(Entity entity) {
        if (!isEnabled()) {
            return;
        }
        if (!(entity instanceof EndCrystalEntity realCrystal)) {
            return;
        }

        boolean attacking = MC.options.attackKey.isPressed();
        BlockPos realPos = realCrystal.getBlockPos();
        long realKey = realPos.asLong();

        Local exact = LOCAL.get(realKey);
        if (exact != null && exact.entity != null && exact.entity.isAlive()) {
            if (consumePendingAttack(realKey, exact, realCrystal)) {
                return;
            }
            if (KoHsCrystalTweaksConfig.get().seamlessEnabled && !attacking) {
                updateAdaptiveTimeout(exact);
                Integer fakeAge = getAge(exact.entity);
                Integer realAge = getAge(realCrystal);
                if (fakeAge == null || realAge == null) {
                    LOCAL.remove(realKey);
                    exact.entity.discard();
                    return;
                }

                int delta = fakeAge - realAge;
                exact.pairedRealId = realCrystal.getId();
                SeamlessCrystalBridge.link(exact.pairedRealId, delta, tick, 1);
                exact.expiresTick = Math.min(exact.expiresTick, tick + 1);
            } else {
                LOCAL.remove(realKey);
                exact.entity.discard();
            }
            return;
        }

        long[] keys = LOCAL.keySet().toLongArray();
        for (long key : keys) {
            BlockPos pos = BlockPos.fromLong(key);
            if (Math.abs(pos.getX() - realPos.getX()) > 1
                    || Math.abs(pos.getY() - realPos.getY()) > 1
                    || Math.abs(pos.getZ() - realPos.getZ()) > 1) {
                continue;
            }

            Local near = LOCAL.get(key);
            if (near == null || near.entity == null || !near.entity.isAlive()) {
                LOCAL.remove(key);
                continue;
            }

            if (KoHsCrystalTweaksConfig.get().seamlessEnabled && !attacking) {
                updateAdaptiveTimeout(near);
                Integer fakeAge = getAge(near.entity);
                Integer realAge = getAge(realCrystal);
                if (fakeAge == null || realAge == null) {
                    LOCAL.remove(key);
                    near.entity.discard();
                    continue;
                }

                int delta = fakeAge - realAge;
                near.pairedRealId = realCrystal.getId();
                SeamlessCrystalBridge.link(near.pairedRealId, delta, tick, 1);
                near.expiresTick = Math.min(near.expiresTick, tick + 1);
                continue;
            }

            LOCAL.remove(key);
            near.entity.discard();
        }
    }

    private static boolean consumePendingAttack(long key, Local local, EndCrystalEntity realCrystal) {
        if (!local.pendingAttack) {
            return false;
        }

        LOCAL.remove(key);
        if (local.pairedRealId != -1) {
            SeamlessCrystalBridge.clear(local.pairedRealId);
        }
        if (local.entity != null && local.entity.isAlive()) {
            local.entity.discard();
        }

        if (!KoHsCrystalTweaksConfig.get().rapidAttackFixEnabled
                || MC.world == null
                || MC.player == null
                || MC.interactionManager == null
                || realCrystal.getEntityWorld() != MC.world
                || !realCrystal.isAlive()
                || !MC.player.canInteractWithEntity(realCrystal, 0.0)) {
            return true;
        }

        // Preserve the normal interaction-manager path and give the existing local
        // removal hook the real server entity rather than the discarded prediction.
        MC.crosshairTarget = new EntityHitResult(realCrystal);
        MC.interactionManager.attackEntity(MC.player, realCrystal);
        return true;
    }

    public static void onEntityUnloaded(Entity entity) {
        if (!isEnabled()) {
            return;
        }
        if (!(entity instanceof EndCrystalEntity)) {
            return;
        }

        SeamlessCrystalBridge.clear(entity.getId());
        BlockPos realPos = entity.getBlockPos();

        long[] keys = LOCAL.keySet().toLongArray();
        for (long key : keys) {
            BlockPos pos = BlockPos.fromLong(key);
            if (pos.getX() != realPos.getX() || pos.getY() != realPos.getY() || pos.getZ() != realPos.getZ()) {
                continue;
            }

            Local local = LOCAL.remove(key);
            if (local != null && local.entity != null && local.entity.isAlive()) {
                local.entity.discard();
            }
            return;
        }
    }

    private static BlockPos resolveBaseFromHit(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos base = hit.getBlockPos();
        return isValidBase(base) ? base : null;
    }

    private static void spawnLocal(BlockPos pos) {
        ClientWorld world = MC.world;
        if (world == null) {
            return;
        }

        Local old = LOCAL.remove(pos.asLong());
        if (old != null && old.entity != null && old.entity.isAlive()) {
            if (old.pairedRealId != -1) {
                SeamlessCrystalBridge.clear(old.pairedRealId);
            }
            old.entity.discard();
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        EndCrystalEntity localCrystal = new EndCrystalEntity(world, x, y, z);
        localCrystal.setShowBottom(false);
        localCrystal.setUuid(UUID.randomUUID());
        localCrystal.setNoGravity(false);
        localCrystal.velocityDirty = true;
        localCrystal.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
        world.addEntity(localCrystal);

        int ttl = resolvePredictionTimeoutTicks();
        LOCAL.put(pos.asLong(), new Local(localCrystal, tick, tick + ttl));
    }

    private static boolean hasAnyRealCrystal(BlockPos pos) {
        return findRealNear(pos) != null;
    }

    private static EndCrystalEntity findRealNear(BlockPos pos) {
        ClientWorld world = MC.world;
        if (world == null) {
            return null;
        }

        Vec3d center = Vec3d.ofBottomCenter(pos);
        Box box = new Box(center.add(-0.5, -0.5, -0.5), center.add(0.5, 1.5, 0.5));
        return world.getEntitiesByType(
                        EntityType.END_CRYSTAL,
                        box,
                        crystal -> !isLocalCrystal(crystal))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static EndCrystalEntity findRealAt(BlockPos pos) {
        ClientWorld world = MC.world;
        if (world == null) {
            return null;
        }

        Vec3d center = Vec3d.ofBottomCenter(pos);
        Box box = new Box(center.add(-0.5, -0.5, -0.5), center.add(0.5, 1.5, 0.5));
        return world.getEntitiesByType(
                        EntityType.END_CRYSTAL,
                        box,
                        crystal -> !isLocalCrystal(crystal) && crystal.getBlockPos().equals(pos))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean isValidBase(BlockPos base) {
        ClientWorld world = MC.world;
        if (world == null) {
            return false;
        }

        BlockState below = world.getBlockState(base);
        if (!below.isOf(Blocks.OBSIDIAN) && !below.isOf(Blocks.BEDROCK)) {
            return false;
        }

        BlockPos above = base.up();
        if (!world.getBlockState(above).isAir()) {
            return false;
        }

        Vec3d center = Vec3d.ofBottomCenter(above);
        Box box = new Box(center.add(-0.5, -0.5, -0.5), center.add(0.5, 1.5, 0.5));
        return world.getOtherEntities(null, box, entity -> entity.isAlive()
                && (!(entity instanceof EndCrystalEntity) || !isLocalCrystal(entity))).isEmpty();
    }

    private static boolean isLocalCrystal(Entity entity) {
        Local local = LOCAL.get(entity.getBlockPos().asLong());
        return local != null && local.entity == entity;
    }

    private static int resolvePredictionTimeoutTicks() {
        int configured = Math.max(2, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks);
        adaptiveTimeoutTicks = Math.max(2, Math.min(adaptiveTimeoutTicks, configured));
        return adaptiveTimeoutTicks;
    }

    private static void updateAdaptiveTimeout(Local local) {
        int configured = Math.max(2, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks);
        int observed = Math.max(2, tick - local.createdTick + 1);
        int blended = ((adaptiveTimeoutTicks * 3) + observed) / 4;
        adaptiveTimeoutTicks = Math.max(2, Math.min(blended, configured));
    }

    public static void clearAll() {
        long[] keys = LOCAL.keySet().toLongArray();
        for (long key : keys) {
            Local local = LOCAL.remove(key);
            if (local != null && local.entity != null && local.entity.isAlive()) {
                local.entity.discard();
            }
        }
        SeamlessCrystalBridge.clearAll();
    }

    private static Integer getAge(Entity entity) {
        if (entity instanceof EntityAgeAccessor accessor) {
            return accessor.kct$getAge();
        }

        Field field = resolveAgeField();
        if (field == null) {
            return null;
        }

        try {
            return field.getInt(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field resolveAgeField() {
        if (ageResolved) {
            return ageField;
        }

        synchronized (CrystalPredictor.class) {
            if (ageResolved) {
                return ageField;
            }
            try {
                Field field = Entity.class.getDeclaredField("age");
                field.setAccessible(true);
                ageField = field;
            } catch (Throwable ignored) {
                ageField = null;
            }
            ageResolved = true;
            return ageField;
        }
    }

    private static final class Local {
        private final EndCrystalEntity entity;
        private final int createdTick;
        private int expiresTick;
        private int pairedRealId = -1;
        private boolean pendingAttack;

        private Local(EndCrystalEntity entity, int createdTick, int expiresTick) {
            this.entity = entity;
            this.createdTick = createdTick;
            this.expiresTick = expiresTick;
        }
    }
}
