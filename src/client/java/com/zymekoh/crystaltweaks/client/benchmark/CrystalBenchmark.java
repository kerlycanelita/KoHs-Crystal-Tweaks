package com.zymekoh.crystaltweaks.client.benchmark;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.compat.HerziumBridge;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.ObsidianDebounce;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

/**
 * The advanced optimizer benchmark: times the player's own crystal play, on screen and on the
 * server, with whatever optimizer is installed.
 *
 * <p>It only watches. Every timestamp is taken where a packet this client was already sending
 * leaves it, or where a packet the server already sent arrives; nothing is placed, attacked, sent,
 * delayed or changed by the benchmark, so it measures the setup as it really plays. That is also why
 * it needs the player to play: a benchmark that clicked by itself would be an autoclicker.</p>
 *
 * <p>Answers from the server are read twice. On the network thread, which is when they reached this
 * computer, and on the game thread, which is when the game acted on them and the player could see
 * it. The gap between the two is the client's own queueing, up to one frame.</p>
 */
public final class CrystalBenchmark {
    /** Breaks the server confirms before a run ends by itself. */
    public static final int TARGET_BREAKS = 30;
    public static final long MAX_DURATION_NANOS = 120_000_000_000L;
    /** How long a placement or an attack waits for the server's answer before it counts as refused. */
    private static final long ANSWER_WINDOW_NANOS = 3_000_000_000L;
    private static final long SWAP_WINDOW_NANOS = 1_000_000_000L;
    private static final long CYCLE_WINDOW_NANOS = 5_000_000_000L;
    /** A gap this long between frames is a pause or a loading screen, not a frame. */
    private static final long FRAME_GAP_NANOS = 250_000_000L;
    private static final int SAMPLE_CAP = 4096;
    private static final int FRAME_CAP = 1 << 17;

    private static final Object LOCK = new Object();
    private static volatile boolean recording;
    private static long startedAt;
    private static long lastFrameAt;
    private static long refusalsAtStart;

    private static final Map<BlockPos, ArrayDeque<Placement>> PENDING_PLACEMENTS = new HashMap<>();
    private static final Map<Integer, SpawnMatch> SPAWNS = new HashMap<>();
    private static final Map<Integer, BlockPos> CRYSTAL_BASES = new HashMap<>();
    private static final Map<Integer, Attack> ATTACKS = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_ATTACK_AT_BASE = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_PLACEMENT_AT_BASE = new HashMap<>();
    private static long lastSwapAt = -1L;

    private static final Samples PLACE_TO_SPAWN_NETWORK = new Samples(SAMPLE_CAP);
    private static final Samples PLACE_TO_SPAWN_SHOWN = new Samples(SAMPLE_CAP);
    private static final Samples ATTACK_TO_EXPLOSION = new Samples(SAMPLE_CAP);
    private static final Samples ATTACK_TO_EXPLOSION_HEARD = new Samples(SAMPLE_CAP);
    /**
     * Every attack of the last two seconds, finished or not: the server removes a crystal before it
     * sends the explosion, so the explosion is matched here rather than against the open attacks.
     */
    private static final java.util.ArrayDeque<double[]> RECENT_ATTACKS = new java.util.ArrayDeque<>();
    private static final long RECENT_ATTACK_NANOS = 2_000_000_000L;
    private static final Samples ATTACK_TO_REMOVAL_NETWORK = new Samples(SAMPLE_CAP);
    private static final Samples ATTACK_TO_REMOVAL_SHOWN = new Samples(SAMPLE_CAP);
    private static final Samples ATTACK_TO_GONE = new Samples(SAMPLE_CAP);
    private static final Samples REPLACE_GAP = new Samples(SAMPLE_CAP);
    private static final Samples CYCLE = new Samples(SAMPLE_CAP);
    private static final Samples SWAP_TO_PLACE = new Samples(SAMPLE_CAP);
    private static final Samples FRAMES = new Samples(FRAME_CAP);
    private static final Samples PING = new Samples(SAMPLE_CAP);

    private static int placements;
    private static int attacks;
    private static int confirmedBreaks;
    private static int predicted;
    private static int removedEarly;
    private static int unconfirmed;
    private static int unmatchedPlacements;

    private static BenchmarkRun lastFinished;

    private CrystalBenchmark() {
    }

    public static boolean recording() {
        return recording;
    }

    /** Starts a run from a clean slate. Only in a world, since there is nothing to time elsewhere. */
    public static void start() {
        synchronized (LOCK) {
            clear();
            long now = System.nanoTime();
            startedAt = now;
            lastFrameAt = 0L;
            refusalsAtStart = ObsidianDebounce.refusedCount();
            recording = true;
        }
    }

    /** Ends the run; a run with enough in it is kept and compared, a shorter one is discarded. */
    public static BenchmarkRun stop() {
        BenchmarkRun run;
        synchronized (LOCK) {
            if (!recording) {
                return null;
            }
            recording = false;
            long now = System.nanoTime();
            expire(now, true);
            run = buildRun(now);
            clear();
        }
        lastFinished = run;
        if (run.meaningful()) {
            BenchmarkStore.add(run);
        }
        return run;
    }

    /** The run that ended most recently this session, kept or not. */
    public static BenchmarkRun lastFinished() {
        return lastFinished;
    }

    public static long elapsedNanos() {
        return recording ? System.nanoTime() - startedAt : 0L;
    }

    public static int confirmedBreaks() {
        return confirmedBreaks;
    }

    public static int placements() {
        return placements;
    }

    // ------------------------------------------------------------------------------------------
    // What this client sends. Game thread.
    // ------------------------------------------------------------------------------------------

    /**
     * A crystal placement just left for the server, aimed at {@code base}, carrying the prediction
     * {@code sequence} the server acknowledges it by.
     */
    public static void onPlacementSent(BlockPos base, int sequence) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            BlockPos key = base.immutable();
            placements++;
            PENDING_PLACEMENTS.computeIfAbsent(key, ignored -> new ArrayDeque<>(4)).addLast(new Placement(now, sequence));
            Long attacked = LAST_ATTACK_AT_BASE.remove(key);
            if (attacked != null && now - attacked <= ANSWER_WINDOW_NANOS) {
                REPLACE_GAP.add(now - attacked);
            }
            Long previous = LAST_PLACEMENT_AT_BASE.put(key, now);
            if (previous != null && now - previous <= CYCLE_WINDOW_NANOS) {
                CYCLE.add(now - previous);
            }
            if (lastSwapAt >= 0L && now - lastSwapAt <= SWAP_WINDOW_NANOS) {
                SWAP_TO_PLACE.add(now - lastSwapAt);
            }
            lastSwapAt = -1L;
        }
    }

    /** An attack on {@code entityId} just left for the server. Any thread. */
    public static void onAttackSent(int entityId) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> recordAttack(entityId, now));
            return;
        }
        recordAttack(entityId, now);
    }

    private static void recordAttack(int entityId, long sentAt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!recording || minecraft.level == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(entityId);
        if (!(target instanceof EndCrystal crystal)) {
            return;
        }
        synchronized (LOCK) {
            BlockPos base = CRYSTAL_BASES.get(entityId);
            if (base == null) {
                base = BlockPos.containing(crystal.getX(), crystal.getY() - 1.0D, crystal.getZ());
            }
            if (ATTACKS.containsKey(entityId)) {
                return; // A second swing at a crystal still in flight changes nothing measured.
            }
            attacks++;
            ATTACKS.put(entityId, new Attack(crystal.position(), sentAt));
            // x, y, z, sent at, heard (0 or 1)
            RECENT_ATTACKS.addLast(new double[] {crystal.getX(), crystal.getY(), crystal.getZ(), sentAt, 0.0D});
            while (RECENT_ATTACKS.size() > 64) {
                RECENT_ATTACKS.pollFirst();
            }
            LAST_ATTACK_AT_BASE.put(base, sentAt);
            // A placement sent before this hit reached the server while the crystal being hit still
            // stood on that block, so it was refused. Left queued, it would claim the next crystal's
            // spawn and time it from a click that never placed it.
            ArrayDeque<Placement> pending = PENDING_PLACEMENTS.get(base);
            if (pending != null) {
                while (!pending.isEmpty() && pending.peekFirst().sentAt() < sentAt) {
                    pending.pollFirst();
                    unmatchedPlacements++;
                }
                if (pending.isEmpty()) {
                    PENDING_PLACEMENTS.remove(base);
                }
            }
        }
    }

    /** The player switched hotbar slots. */
    public static void onCarriedItemSent() {
        if (!recording) {
            return;
        }
        synchronized (LOCK) {
            lastSwapAt = System.nanoTime();
        }
    }

    /** Crystal Tweaks' own prediction just hid {@code entityId}. */
    public static void onPredictedHidden(int entityId) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            Attack attack = ATTACKS.get(entityId);
            if (attack != null && attack.hiddenAt < 0L) {
                attack.hiddenAt = now;
                predicted++;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // What the server sends. Network thread first, then the game thread.
    // ------------------------------------------------------------------------------------------

    /** A crystal the server spawned, on either thread. */
    public static void onCrystalSpawn(int entityId, double x, double y, double z, boolean network) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            BlockPos base = BlockPos.containing(x, y - 1.0D, z);
            SpawnMatch match = SPAWNS.get(entityId);
            if (match == null) {
                ArrayDeque<Placement> pending = PENDING_PLACEMENTS.get(base);
                Placement placement = pending == null ? null : pending.pollFirst();
                if (pending != null && pending.isEmpty()) {
                    PENDING_PLACEMENTS.remove(base);
                }
                if (placement == null || now - placement.sentAt() > ANSWER_WINDOW_NANOS) {
                    return; // Someone else's crystal, or an answer too late to belong to this run.
                }
                match = new SpawnMatch(placement.sentAt());
                SPAWNS.put(entityId, match);
                CRYSTAL_BASES.put(entityId, base);
            }
            if (network && match.networkAt < 0L) {
                match.networkAt = now;
                PLACE_TO_SPAWN_NETWORK.add(now - match.placedAt);
            } else if (!network && match.shownAt < 0L) {
                match.shownAt = now;
                PLACE_TO_SPAWN_SHOWN.add(now - match.placedAt);
            }
        }
    }

    /**
     * The server acknowledged every prediction up to {@code sequence}. It sends a placed crystal the
     * moment it places it and this acknowledgement only at the end of its tick, so a placement it
     * covers that is still waiting here placed nothing: the spot was taken or the click refused.
     * Network thread, where the two arrive in the order the server sent them.
     */
    public static void onPlacementsAcknowledged(int sequence) {
        if (!recording) {
            return;
        }
        synchronized (LOCK) {
            Iterator<Map.Entry<BlockPos, ArrayDeque<Placement>>> iterator = PENDING_PLACEMENTS.entrySet().iterator();
            while (iterator.hasNext()) {
                ArrayDeque<Placement> pending = iterator.next().getValue();
                Iterator<Placement> placementsIterator = pending.iterator();
                while (placementsIterator.hasNext()) {
                    if (placementsIterator.next().sequence() <= sequence) {
                        placementsIterator.remove();
                        unmatchedPlacements++;
                    }
                }
                if (pending.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    /** An explosion the server reported; a crystal break shows up here before its removal does. */
    public static void onExplosion(Vec3 center, boolean network) {
        if (!recording || !network || center == null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            for (Attack attack : ATTACKS.values()) {
                if (attack.explosionAt < 0L && attack.position.distanceToSqr(center) < 0.0625D) {
                    attack.explosionAt = now;
                    ATTACK_TO_EXPLOSION.add(now - attack.sentAt);
                    return;
                }
            }
        }
    }

    /**
     * The explosion's sound and burst just played on this client, at (x, y, z): at the hit when
     * Crystal Tweaks predicted it, or when the server's explosion was handled otherwise. Game thread.
     */
    public static void onExplosionHeard(double x, double y, double z) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            while (!RECENT_ATTACKS.isEmpty() && now - (long) RECENT_ATTACKS.peekFirst()[3] > RECENT_ATTACK_NANOS) {
                RECENT_ATTACKS.pollFirst();
            }
            for (double[] attack : RECENT_ATTACKS) {
                double dx = attack[0] - x;
                double dy = attack[1] - y;
                double dz = attack[2] - z;
                if (attack[4] == 0.0D && dx * dx + dy * dy + dz * dz < 0.0625D) {
                    attack[4] = 1.0D;
                    ATTACK_TO_EXPLOSION_HEARD.add(now - (long) attack[3]);
                    return;
                }
            }
        }
    }

    /** Entities the server removed, on either thread. */
    public static void onRemoval(IntList entityIds, boolean network) {
        if (!recording || entityIds == null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            for (int index = 0; index < entityIds.size(); index++) {
                int entityId = entityIds.getInt(index);
                Attack attack = ATTACKS.get(entityId);
                if (attack == null) {
                    CRYSTAL_BASES.remove(entityId);
                    SPAWNS.remove(entityId);
                    continue;
                }
                if (network && attack.removalNetworkAt < 0L) {
                    attack.removalNetworkAt = now;
                    confirmedBreaks++;
                    ATTACK_TO_REMOVAL_NETWORK.add(now - attack.sentAt);
                } else if (!network && attack.removalShownAt < 0L) {
                    if (attack.removalNetworkAt < 0L) {
                        confirmedBreaks++; // The network read was missed; the break still happened.
                    }
                    attack.removalShownAt = now;
                    ATTACK_TO_REMOVAL_SHOWN.add(now - attack.sentAt);
                    finish(attack);
                    ATTACKS.remove(entityId);
                    CRYSTAL_BASES.remove(entityId);
                    SPAWNS.remove(entityId);
                }
            }
        }
    }

    /**
     * A crystal left the client's world. Before the server's removal arrived, that is another mod
     * removing it locally: the way client-side crystal optimizers make a break instant.
     */
    public static void onCrystalUnloaded(int entityId) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            Attack attack = ATTACKS.get(entityId);
            if (attack != null && attack.removalShownAt < 0L && attack.localRemovalAt < 0L) {
                attack.localRemovalAt = now;
                removedEarly++;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Frames and housekeeping. Game thread.
    // ------------------------------------------------------------------------------------------

    public static void onFrame(long now) {
        if (!recording) {
            return;
        }
        long previous = lastFrameAt;
        lastFrameAt = now;
        if (previous > 0L && now - previous < FRAME_GAP_NANOS) {
            synchronized (LOCK) {
                FRAMES.add(now - previous);
            }
        }
    }

    /** Once per client tick: expires what the server never answered and ends a finished run. */
    public static void tick(Minecraft minecraft) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        boolean done;
        synchronized (LOCK) {
            expire(now, false);
            samplePing(minecraft);
            done = confirmedBreaks >= TARGET_BREAKS || now - startedAt >= MAX_DURATION_NANOS;
        }
        if (done) {
            BenchmarkRun run = stop();
            announce(minecraft, run);
        }
    }

    /** The world was left mid-run: keep what was measured if it is enough to mean anything. */
    public static void onDisconnect() {
        if (recording) {
            stop();
        }
    }

    private static void announce(Minecraft minecraft, BenchmarkRun run) {
        if (minecraft.player == null || run == null) {
            return;
        }
        boolean spanish = minecraft.getLanguageManager().getSelected().startsWith("es");
        String text = run.meaningful()
                ? (spanish
                        ? "[Crystal Tweaks] Benchmark terminado. Míralo en Avanzado › Benchmark de optimizadores."
                        : "[Crystal Tweaks] Benchmark finished. See it in Advanced › Optimizer benchmark.")
                : (spanish
                        ? "[Crystal Tweaks] Benchmark detenido: no hubo suficientes cristales para medir."
                        : "[Crystal Tweaks] Benchmark stopped: not enough crystals to measure.");
        CrystalUi.chat(Component.literal(text));
    }

    private static void expire(long now, boolean everything) {
        Iterator<Map.Entry<Integer, Attack>> attacksIterator = ATTACKS.entrySet().iterator();
        while (attacksIterator.hasNext()) {
            Attack attack = attacksIterator.next().getValue();
            if (everything || now - attack.sentAt > ANSWER_WINDOW_NANOS) {
                if (attack.removalNetworkAt < 0L && attack.removalShownAt < 0L) {
                    unconfirmed++;
                }
                finish(attack);
                attacksIterator.remove();
            }
        }
        Iterator<Map.Entry<BlockPos, ArrayDeque<Placement>>> placementsIterator = PENDING_PLACEMENTS.entrySet().iterator();
        while (placementsIterator.hasNext()) {
            ArrayDeque<Placement> pending = placementsIterator.next().getValue();
            while (!pending.isEmpty() && (everything || now - pending.peekFirst().sentAt() > ANSWER_WINDOW_NANOS)) {
                pending.pollFirst();
                unmatchedPlacements++;
            }
            if (pending.isEmpty()) {
                placementsIterator.remove();
            }
        }
        if (CRYSTAL_BASES.size() > 512) {
            CRYSTAL_BASES.clear();
            SPAWNS.clear();
        }
    }

    /** The moment the crystal stopped being drawn: the earliest of hidden, removed locally or removed. */
    private static void finish(Attack attack) {
        long gone = Long.MAX_VALUE;
        if (attack.hiddenAt >= 0L) {
            gone = Math.min(gone, attack.hiddenAt);
        }
        if (attack.localRemovalAt >= 0L) {
            gone = Math.min(gone, attack.localRemovalAt);
        }
        if (attack.removalShownAt >= 0L) {
            gone = Math.min(gone, attack.removalShownAt);
        }
        if (gone != Long.MAX_VALUE) {
            ATTACK_TO_GONE.add(gone - attack.sentAt);
        }
    }

    private static void samplePing(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null
                || minecraft.level.getGameTime() % 20L != 0L) {
            return;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        if (info != null) {
            PING.add(info.getLatency() * 1_000_000L);
        }
    }

    private static BenchmarkRun buildRun(long now) {
        Minecraft minecraft = Minecraft.getInstance();
        BenchmarkStats frames = FRAMES.stats();
        double averageFps = frames.present() && frames.mean() > 0 ? 1000.0D / frames.mean() : 0.0D;
        double onePercentLow = frames.present() && frames.p99() > 0 ? 1000.0D / frames.p99() : 0.0D;
        return new BenchmarkRun(
                System.currentTimeMillis(),
                version("minecraft"),
                version(CrystalTweaksClient.MOD_ID),
                environment(minecraft),
                serverBrand(minecraft),
                optimizerInCharge(),
                detectedOptimizers(),
                CrystalVisualConfig.herziumIntegration() && HerziumBridge.installed() ? HerziumBridge.hotbarOrder() : "",
                (now - startedAt) / 1_000_000L,
                placements,
                attacks,
                confirmedBreaks,
                predicted,
                removedEarly,
                unconfirmed,
                unmatchedPlacements,
                ObsidianDebounce.refusedCount() - refusalsAtStart,
                PING.stats().median(),
                PLACE_TO_SPAWN_NETWORK.stats(),
                PLACE_TO_SPAWN_SHOWN.stats(),
                ATTACK_TO_EXPLOSION.stats(),
                ATTACK_TO_REMOVAL_NETWORK.stats(),
                ATTACK_TO_REMOVAL_SHOWN.stats(),
                ATTACK_TO_GONE.stats(),
                REPLACE_GAP.stats(),
                CYCLE.stats(),
                SWAP_TO_PLACE.stats(),
                frames,
                averageFps,
                onePercentLow,
                ATTACK_TO_EXPLOSION_HEARD.stats());
    }

    /** Who was handling crystals during the run, in words a player recognizes. */
    public static String optimizerInCharge() {
        if (CrystalOptimizerGuard.forcedOff()) {
            List<String> others = detectedOptimizers();
            return others.isEmpty() ? "Vanilla (Crystal Tweaks forced off)" : String.join(", ", others);
        }
        if (CrystalOptimizerGuard.conflictDetected()) {
            String name = CrystalOptimizerGuard.conflictingModName();
            return name.isBlank() ? "Another crystal optimizer" : name;
        }
        return "Crystal Tweaks";
    }

    /** Every installed mod that names itself a crystal optimizer or is a known one. */
    public static List<String> detectedOptimizers() {
        List<String> names = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            if (CrystalTweaksClient.MOD_ID.equals(id)) {
                continue;
            }
            String name = container.getMetadata().getName();
            if (CrystalOptimizerGuard.looksLikeOptimizer(id, name)) {
                names.add((name == null || name.isBlank() ? id : name) + " "
                        + container.getMetadata().getVersion().getFriendlyString());
            }
        }
        return List.copyOf(names);
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    private static String environment(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            return minecraft.getSingleplayerServer().isPublished() ? "lan-host" : "singleplayer";
        }
        return minecraft.getCurrentServer() != null && minecraft.getCurrentServer().isLan() ? "lan" : "multiplayer";
    }

    private static String serverBrand(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        String brand = connection == null ? null : connection.serverBrand();
        return brand == null ? "" : brand;
    }

    private static void clear() {
        PENDING_PLACEMENTS.clear();
        SPAWNS.clear();
        CRYSTAL_BASES.clear();
        ATTACKS.clear();
        LAST_ATTACK_AT_BASE.clear();
        LAST_PLACEMENT_AT_BASE.clear();
        lastSwapAt = -1L;
        PLACE_TO_SPAWN_NETWORK.clear();
        PLACE_TO_SPAWN_SHOWN.clear();
        ATTACK_TO_EXPLOSION.clear();
        ATTACK_TO_EXPLOSION_HEARD.clear();
        RECENT_ATTACKS.clear();
        ATTACK_TO_REMOVAL_NETWORK.clear();
        ATTACK_TO_REMOVAL_SHOWN.clear();
        ATTACK_TO_GONE.clear();
        REPLACE_GAP.clear();
        CYCLE.clear();
        SWAP_TO_PLACE.clear();
        FRAMES.clear();
        PING.clear();
        placements = 0;
        attacks = 0;
        confirmedBreaks = 0;
        predicted = 0;
        removedEarly = 0;
        unconfirmed = 0;
        unmatchedPlacements = 0;
    }

    private record Placement(long sentAt, int sequence) {
    }

    private static final class SpawnMatch {
        private final long placedAt;
        private long networkAt = -1L;
        private long shownAt = -1L;

        private SpawnMatch(long placedAt) {
            this.placedAt = placedAt;
        }
    }

    private static final class Attack {
        private final Vec3 position;
        private final long sentAt;
        private long hiddenAt = -1L;
        private long localRemovalAt = -1L;
        private long explosionAt = -1L;
        private long heardAt = -1L;
        private long removalNetworkAt = -1L;
        private long removalShownAt = -1L;

        private Attack(Vec3 position, long sentAt) {
            this.position = position;
            this.sentAt = sentAt;
        }
    }

    /** Nanosecond samples, reported in milliseconds. Bounded: past the cap new samples are dropped. */
    private static final class Samples {
        private final long[] values;
        private int size;

        private Samples(int capacity) {
            this.values = new long[capacity];
        }

        void add(long nanos) {
            if (this.size < this.values.length && nanos >= 0L) {
                this.values[this.size++] = nanos;
            }
        }

        void clear() {
            this.size = 0;
        }

        BenchmarkStats stats() {
            double[] millis = new double[this.size];
            for (int index = 0; index < this.size; index++) {
                millis[index] = this.values[index] / 1_000_000.0D;
            }
            return BenchmarkStats.of(millis, this.size);
        }
    }
}
