package dev.zymekoh.kohscrystaltweaks.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Always-on, input-preserving Crystal PvP fast path.
 *
 * <p>It remembers only crystal placements already accepted by vanilla. A pending attack is created
 * only by a real player attack input on that placement. The attack is sent once the server-provided
 * entity ID exists; IDs are never guessed and no synthetic clicks are generated.</p>
 */
public final class CrystalInteractionFastPath {
    private static final int MAX_PENDING_TICKS = 40;
    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    private static final Long2ObjectOpenHashMap<PendingPlacement> PENDING = new Long2ObjectOpenHashMap<>();
    private static final Int2IntOpenHashMap LAST_REAL_ATTACK_TICK = new Int2IntOpenHashMap();

    private static int clientTick;

    static {
        LAST_REAL_ATTACK_TICK.defaultReturnValue(NO_TICK);
    }

    private CrystalInteractionFastPath() {
    }

    public static void clientTick() {
        clientTick++;
        LAST_REAL_ATTACK_TICK.clear();

        ObjectIterator<Long2ObjectMap.Entry<PendingPlacement>> iterator =
                PENDING.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<PendingPlacement> entry = iterator.next();
            if (isExpired(entry.getValue().createdAtTick, clientTick)) {
                iterator.remove();
            }
        }
    }

    public static void reset() {
        PENDING.clear();
        LAST_REAL_ATTACK_TICK.clear();
        clientTick = 0;
    }

    public static boolean recordCrystalPlacement(BlockHitResult hit) {
        if (hit == null || MINECRAFT.level == null) {
            return false;
        }

        BlockPos base = hit.getBlockPos();
        BlockState state = MINECRAFT.level.getBlockState(base);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return false;
        }

        long positionKey = base.above().asLong();
        PendingPlacement previous = PENDING.get(positionKey);
        boolean preserveAttack = previous != null
                && previous.attackRequested
                && !isExpired(previous.createdAtTick, clientTick);
        PENDING.put(positionKey, new PendingPlacement(clientTick, preserveAttack));
        return true;
    }

    /** Claims a left-click on the exact base of a recently accepted crystal placement. */
    public static boolean requestAttackForBase(Player player, BlockPos base) {
        if (player == null || base == null) {
            return false;
        }

        long positionKey = base.above().asLong();
        PendingPlacement pending = usablePending(positionKey);
        if (pending == null) {
            return false;
        }

        pending.attackRequested = true;
        EndCrystal realCrystal = findKnownRealCrystal(positionKey, pending);
        if (realCrystal != null) {
            dispatchPendingAttack(positionKey, realCrystal);
        }
        return true;
    }

    /** Records the same explicit attack when the target was a local predicted crystal. */
    public static boolean requestAttackForLocal(Entity localCrystal) {
        if (!CrystalPredictor.isLocalCrystalEntity(localCrystal)) {
            return false;
        }

        long positionKey = localCrystal.blockPosition().asLong();
        PendingPlacement pending = usablePending(positionKey);
        if (pending == null) {
            return false;
        }

        pending.attackRequested = true;
        EndCrystal realCrystal = findKnownRealCrystal(positionKey, pending);
        if (realCrystal != null) {
            dispatchPendingAttack(positionKey, realCrystal);
        }
        return true;
    }

    public static boolean shouldSuppressMining(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return false;
        }
        PendingPlacement pending = usablePending(blockHit.getBlockPos().above().asLong());
        return pending != null && pending.attackRequested;
    }

    public static boolean hasRecentPlacementBase(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return false;
        }
        return usablePending(blockHit.getBlockPos().above().asLong()) != null;
    }

    public static void onEntityLoaded(Entity entity) {
        if (!(entity instanceof EndCrystal realCrystal) || CrystalPredictor.isLocalCrystalEntity(entity)) {
            return;
        }

        long positionKey = realCrystal.blockPosition().asLong();
        PendingPlacement pending = usablePending(positionKey);
        if (pending == null) {
            return;
        }

        pending.realEntityId = realCrystal.getId();
        if (pending.attackRequested) {
            dispatchPendingAttack(positionKey, realCrystal);
        }
    }

    public static void onEntityUnloaded(Entity entity) {
        if (!(entity instanceof EndCrystal) || CrystalPredictor.isLocalCrystalEntity(entity)) {
            return;
        }

        long positionKey = entity.blockPosition().asLong();
        PendingPlacement pending = PENDING.get(positionKey);
        if (pending != null && pending.realEntityId == entity.getId()) {
            PENDING.remove(positionKey);
        }
    }

    public static EndCrystal findKnownRealCrystal(BlockPos crystalPosition) {
        if (crystalPosition == null) {
            return null;
        }
        long positionKey = crystalPosition.asLong();
        PendingPlacement pending = usablePending(positionKey);
        return pending == null ? null : findKnownRealCrystal(positionKey, pending);
    }

    /** Returns false for redundant attacks against the same real entity in one client tick. */
    public static boolean claimRealCrystalAttack(Entity target) {
        if (!(target instanceof EndCrystal) || CrystalPredictor.isLocalCrystalEntity(target)) {
            return true;
        }

        int entityId = target.getId();
        int previousTick = LAST_REAL_ATTACK_TICK.put(entityId, clientTick);
        if (isDuplicateAttack(previousTick, clientTick)) {
            return false;
        }

        long positionKey = target.blockPosition().asLong();
        PendingPlacement pending = PENDING.get(positionKey);
        if (pending != null && (pending.realEntityId == -1 || pending.realEntityId == entityId)) {
            PENDING.remove(positionKey);
        }
        return true;
    }

    public static boolean wasRealCrystalAttackedThisTick(int entityId) {
        return LAST_REAL_ATTACK_TICK.get(entityId) == clientTick;
    }

    private static PendingPlacement usablePending(long positionKey) {
        PendingPlacement pending = PENDING.get(positionKey);
        if (pending != null && isExpired(pending.createdAtTick, clientTick)) {
            PENDING.remove(positionKey);
            return null;
        }
        return pending;
    }

    private static EndCrystal findKnownRealCrystal(long positionKey, PendingPlacement pending) {
        if (pending.realEntityId == -1 || MINECRAFT.level == null) {
            return null;
        }
        Entity entity = MINECRAFT.level.getEntity(pending.realEntityId);
        if (entity instanceof EndCrystal crystal
                && !CrystalPredictor.isLocalCrystalEntity(crystal)
                && crystal.isAlive()
                && crystal.blockPosition().asLong() == positionKey) {
            return crystal;
        }
        return null;
    }

    private static void dispatchPendingAttack(long positionKey, EndCrystal realCrystal) {
        PENDING.remove(positionKey);
        if (MINECRAFT.level == null
                || MINECRAFT.player == null
                || MINECRAFT.gameMode == null
                || realCrystal.level() != MINECRAFT.level
                || !realCrystal.isAlive()
                || !MINECRAFT.player.isWithinEntityInteractionRange(realCrystal, 0.0D)) {
            return;
        }

        MINECRAFT.crosshairPickEntity = realCrystal;
        MINECRAFT.hitResult = new EntityHitResult(realCrystal);
        MINECRAFT.gameMode.attack(MINECRAFT.player, realCrystal);
    }

    static boolean isExpired(int createdAtTick, int currentTick) {
        return currentTick - createdAtTick > MAX_PENDING_TICKS;
    }

    static boolean isDuplicateAttack(int previousAttackTick, int currentTick) {
        return previousAttackTick == currentTick;
    }

    private static final class PendingPlacement {
        private final int createdAtTick;
        private boolean attackRequested;
        private int realEntityId = -1;

        private PendingPlacement(int createdAtTick, boolean attackRequested) {
            this.createdAtTick = createdAtTick;
            this.attackRequested = attackRequested;
        }
    }
}
