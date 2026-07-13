package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.compat.CrystalAnchorCounterCompat;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CrystalPredictor {
    private static final String LOCAL_ENTITY_TAG = "kohs_crystal_tweaks.local_prediction";
    private static final long NO_POSITION = Long.MIN_VALUE;
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    private static final Long2ObjectOpenHashMap<LocalPrediction> PREDICTIONS = new Long2ObjectOpenHashMap<>();
    private static final Int2LongOpenHashMap LOCAL_ENTITY_TO_POSITION = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap REAL_ENTITY_TO_POSITION = new Int2LongOpenHashMap();
    private static final Predicate<Entity> PICKABLE_NON_LOCAL = entity ->
            EntitySelector.CAN_BE_PICKED.test(entity) && !isLocalCrystalEntity(entity);

    private static boolean enabled = true;
    private static int clientTick;
    private static int adaptiveTimeoutTicks = 12;

    static {
        LOCAL_ENTITY_TO_POSITION.defaultReturnValue(NO_POSITION);
        REAL_ENTITY_TO_POSITION.defaultReturnValue(NO_POSITION);
    }

    private CrystalPredictor() {
    }

    public static int currentTick() {
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

    public static void reset() {
        clearAll();
        clientTick = 0;
        adaptiveTimeoutTicks = configuredTimeout();
    }

    public static boolean isLocalCrystalEntity(Entity entity) {
        return entity instanceof EndCrystal && entity.entityTags().contains(LOCAL_ENTITY_TAG);
    }

    public static void onLocalCrystalAttack(Entity entity) {
        if (!isLocalCrystalEntity(entity)) {
            return;
        }

        long positionKey = LOCAL_ENTITY_TO_POSITION.get(entity.getId());
        if (positionKey == NO_POSITION) {
            return;
        }

        LocalPrediction prediction = PREDICTIONS.get(positionKey);
        boolean unpaired = prediction != null && prediction.pairedRealEntityId == -1;
        removePrediction(positionKey);
        if (unpaired) {
            CrystalAnchorCounterCompat.recordCrystalBreak(entity.getId(), entity.getUUID());
        }
    }

    public static HitResult raycastIgnoringLocal(float partialTick) {
        if (MINECRAFT.level == null || MINECRAFT.player == null) {
            return BlockHitResult.miss(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO);
        }

        Entity camera = MINECRAFT.getCameraEntity();
        if (camera == null) {
            return BlockHitResult.miss(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO);
        }

        double blockRange = MINECRAFT.player.blockInteractionRange();
        double entityRange = MINECRAFT.player.entityInteractionRange();
        double maximumRange = Math.max(blockRange, entityRange);
        double maximumRangeSquared = maximumRange * maximumRange;
        Vec3 origin = camera.getEyePosition(partialTick);
        HitResult blockHit = camera.pick(maximumRange, partialTick, false);
        double blockDistanceSquared = blockHit.getLocation().distanceToSqr(origin);

        if (blockHit.getType() != HitResult.Type.MISS) {
            maximumRangeSquared = blockDistanceSquared;
            maximumRange = Math.sqrt(maximumRangeSquared);
        }

        Vec3 direction = camera.getViewVector(partialTick);
        Vec3 destination = origin.add(direction.scale(maximumRange));
        AABB searchBox = camera.getBoundingBox()
                .expandTowards(direction.scale(maximumRange))
                .inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                camera,
                origin,
                destination,
                searchBox,
                PICKABLE_NON_LOCAL,
                maximumRangeSquared);

        if (entityHit != null && entityHit.getLocation().distanceToSqr(origin) < blockDistanceSquared) {
            return filterToRange(entityHit, origin, entityRange);
        }
        return filterToRange(blockHit, origin, blockRange);
    }

    public static void clientTick() {
        if (!isEnabled()) {
            if (!PREDICTIONS.isEmpty()) {
                clearAll();
            }
            return;
        }
        if (MINECRAFT.level == null) {
            return;
        }

        clientTick++;
        ObjectIterator<Long2ObjectMap.Entry<LocalPrediction>> iterator =
                PREDICTIONS.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<LocalPrediction> entry = iterator.next();
            LocalPrediction prediction = entry.getValue();
            if (prediction.entity.isRemoved() || clientTick >= prediction.expiresAtTick) {
                iterator.remove();
                discardDetachedPrediction(prediction, prediction.pairedRealEntityId != -1);
            }
        }
    }

    public static void onSuccessfulCrystalUse(BlockHitResult hit) {
        if (!isEnabled() || MINECRAFT.player == null || MINECRAFT.level == null) {
            return;
        }

        BlockPos base = hit.getBlockPos();
        if (!isValidBase(base)) {
            return;
        }

        BlockPos crystalPosition = base.above();
        long positionKey = crystalPosition.asLong();
        if (hasRealCrystal(crystalPosition)) {
            return;
        }

        LocalPrediction existing = PREDICTIONS.get(positionKey);
        if (existing != null) {
            if (existing.pairedRealEntityId != -1) {
                return;
            }
            if (clientTick - existing.createdAtTick < 2) {
                existing.expiresAtTick = Math.min(existing.expiresAtTick, clientTick + 2);
                return;
            }
            removePrediction(positionKey);
        }

        if (!hasPlacementSpace(crystalPosition)) {
            return;
        }
        spawnLocal(crystalPosition);
    }

    public static void onEntityLoaded(Entity entity) {
        if (!isEnabled() || isLocalCrystalEntity(entity) || !(entity instanceof EndCrystal realCrystal)) {
            return;
        }

        LocalPrediction prediction = findPredictionFor(realCrystal.blockPosition());
        if (prediction == null) {
            return;
        }

        long positionKey = LOCAL_ENTITY_TO_POSITION.get(prediction.entity.getId());
        if (positionKey == NO_POSITION) {
            return;
        }

        boolean attacking = MINECRAFT.options.keyAttack.isDown();
        if (!KoHsCrystalTweaksConfig.get().seamlessEnabled || attacking) {
            removePrediction(positionKey);
            return;
        }

        updateAdaptiveTimeout(prediction);
        prediction.pairedRealEntityId = realCrystal.getId();
        REAL_ENTITY_TO_POSITION.put(realCrystal.getId(), positionKey);
        SeamlessCrystalBridge.link(
                realCrystal.getId(),
                prediction.entity.time - realCrystal.time,
                clientTick,
                0);
        prediction.expiresAtTick = Math.min(prediction.expiresAtTick, clientTick + 1);
    }

    public static void onEntityUnloaded(Entity entity) {
        if (isLocalCrystalEntity(entity) || !(entity instanceof EndCrystal)) {
            return;
        }

        int entityId = entity.getId();
        SeamlessCrystalBridge.clear(entityId);
        long positionKey = REAL_ENTITY_TO_POSITION.remove(entityId);
        if (positionKey != NO_POSITION) {
            removePrediction(positionKey);
        }
    }

    public static void clearAll() {
        for (LocalPrediction prediction : PREDICTIONS.values()) {
            if (!prediction.entity.isRemoved()) {
                prediction.entity.discard();
            }
        }
        PREDICTIONS.clear();
        LOCAL_ENTITY_TO_POSITION.clear();
        REAL_ENTITY_TO_POSITION.clear();
        SeamlessCrystalBridge.clearAll();
    }

    private static HitResult filterToRange(HitResult hit, Vec3 origin, double maximumRange) {
        Vec3 location = hit.getLocation();
        if (location.closerThan(origin, maximumRange)) {
            return hit;
        }

        Direction direction = Direction.getApproximateNearest(
                location.x - origin.x,
                location.y - origin.y,
                location.z - origin.z);
        return BlockHitResult.miss(location, direction, BlockPos.containing(location));
    }

    private static LocalPrediction findPredictionFor(BlockPos realPosition) {
        LocalPrediction exact = PREDICTIONS.get(realPosition.asLong());
        if (isUsable(exact) && exact.pairedRealEntityId == -1) {
            return exact;
        }

        LocalPrediction nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        ObjectIterator<Long2ObjectMap.Entry<LocalPrediction>> iterator =
                PREDICTIONS.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<LocalPrediction> entry = iterator.next();
            LocalPrediction candidate = entry.getValue();
            if (!isUsable(candidate) || candidate.pairedRealEntityId != -1) {
                continue;
            }

            long key = entry.getLongKey();
            int dx = Math.abs(BlockPos.getX(key) - realPosition.getX());
            int dy = Math.abs(BlockPos.getY(key) - realPosition.getY());
            int dz = Math.abs(BlockPos.getZ(key) - realPosition.getZ());
            if (dx > 1 || dy > 1 || dz > 1) {
                continue;
            }

            int distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean isUsable(LocalPrediction prediction) {
        return prediction != null && !prediction.entity.isRemoved();
    }

    private static boolean isValidBase(BlockPos base) {
        ClientLevel level = MINECRAFT.level;
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(base);
        return (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK))
                && level.isEmptyBlock(base.above());
    }

    private static boolean hasPlacementSpace(BlockPos crystalPosition) {
        ClientLevel level = MINECRAFT.level;
        if (level == null) {
            return false;
        }

        AABB placementBox = placementBox(crystalPosition);
        return level.getEntities((Entity) null, placementBox, entity -> !isLocalCrystalEntity(entity)).isEmpty();
    }

    private static boolean hasRealCrystal(BlockPos crystalPosition) {
        ClientLevel level = MINECRAFT.level;
        if (level == null) {
            return false;
        }

        return !level.getEntities(
                EntityType.END_CRYSTAL,
                placementBox(crystalPosition),
                crystal -> !isLocalCrystalEntity(crystal)).isEmpty();
    }

    private static AABB placementBox(BlockPos position) {
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();
        return new AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0);
    }

    private static void spawnLocal(BlockPos position) {
        ClientLevel level = MINECRAFT.level;
        if (level == null) {
            return;
        }

        EndCrystal crystal = new EndCrystal(
                level,
                position.getX() + 0.5,
                position.getY(),
                position.getZ() + 0.5);
        crystal.time = 0;
        crystal.setShowBottom(false);
        crystal.setUUID(UUID.randomUUID());
        crystal.setNoGravity(true);
        crystal.addTag(LOCAL_ENTITY_TAG);

        long positionKey = position.asLong();
        LocalPrediction prediction = new LocalPrediction(
                crystal,
                clientTick,
                clientTick + adaptiveTimeout());
        PREDICTIONS.put(positionKey, prediction);
        LOCAL_ENTITY_TO_POSITION.put(crystal.getId(), positionKey);
        level.addEntity(crystal);
    }

    private static void removePrediction(long positionKey) {
        LocalPrediction prediction = PREDICTIONS.remove(positionKey);
        if (prediction != null) {
            discardDetachedPrediction(prediction, false);
        }
    }

    private static void discardDetachedPrediction(LocalPrediction prediction, boolean preserveRealBridge) {
        LOCAL_ENTITY_TO_POSITION.remove(prediction.entity.getId());
        if (prediction.pairedRealEntityId != -1) {
            REAL_ENTITY_TO_POSITION.remove(prediction.pairedRealEntityId);
            if (!preserveRealBridge) {
                SeamlessCrystalBridge.clear(prediction.pairedRealEntityId);
            }
        }
        if (!prediction.entity.isRemoved()) {
            prediction.entity.discard();
        }
    }

    private static int configuredTimeout() {
        return Math.max(3, KoHsCrystalTweaksConfig.get().predictionTimeoutTicks);
    }

    private static int adaptiveTimeout() {
        adaptiveTimeoutTicks = Math.clamp(adaptiveTimeoutTicks, 3, configuredTimeout());
        return adaptiveTimeoutTicks;
    }

    private static void updateAdaptiveTimeout(LocalPrediction prediction) {
        int observedTicks = Math.max(1, clientTick - prediction.createdAtTick);
        int target = observedTicks + 2;
        int blended = Math.round(adaptiveTimeoutTicks * 0.75F + target * 0.25F);
        adaptiveTimeoutTicks = Math.clamp(blended, 3, configuredTimeout());
    }

    private static final class LocalPrediction {
        private final EndCrystal entity;
        private final int createdAtTick;
        private int expiresAtTick;
        private int pairedRealEntityId = -1;

        private LocalPrediction(EndCrystal entity, int createdAtTick, int expiresAtTick) {
            this.entity = entity;
            this.createdAtTick = createdAtTick;
            this.expiresAtTick = expiresAtTick;
        }
    }
}
