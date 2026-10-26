package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmark;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Marks a crystal the player just attacked as visually broken, without touching the world.
 *
 * <p>The entity stays in the client level exactly as Vanilla left it. Only its rendering is
 * skipped, so the break looks immediate while the crosshair target, the interaction ranges, the
 * use cooldown and every outgoing packet remain the ones an unmodified client would produce.</p>
 *
 * <p>Removing the entity instead, as earlier versions did, changes what the next click targets and
 * therefore which packet Vanilla sends next. It also desynchronises the client whenever the server
 * refuses the hit, leaving a crystal the server still tracks and the player can no longer see.
 * Hiding avoids both: a prediction the server refuses simply becomes visible again, with a low note
 * so the player knows the crystal standing there again is a refused hit, not a new one.</p>
 */
public final class CrystalBreakPrediction {
    /**
     * How long a crystal stays hidden when the connection is quick. Comfortably longer than the
     * round trip it covers, and short enough that a refused hit corrects itself almost at once.
     */
    private static final long BASE_HIDE_NANOS = 250_000_000L;

    /**
     * Upper bound for the hidden window. Past this the connection is bad enough that continuing to
     * hide a crystal the server may still be tracking is worse than showing it again.
     */
    private static final long MAX_HIDE_NANOS = 1_000_000_000L;

    /**
     * Round-trip time above which the window starts following the measured latency instead of the
     * base value. Below it the base window already outlasts the round trip by a wide margin.
     */
    private static final long REINFORCE_ABOVE_MILLIS = 70L;

    private static final int MAX_TRACKED = 64;

    private static final Map<Integer, Long> HIDDEN = new ConcurrentHashMap<>();

    /** The hidden crystals that are the player's own hits, not removals read off the network, and when each was hit. */
    private static final Map<Integer, Long> PREDICTED = new ConcurrentHashMap<>();

    /**
     * The crystals this client knows of, by entity id, kept from the client thread so the network
     * thread can tell a crystal's removal from any other entity's without touching the world.
     */
    private static final Set<Integer> CRYSTALS = ConcurrentHashMap.newKeySet();

    /** How long a crystal the server removed stays undrawn before the world catches up, at most. */
    private static final long REMOVAL_HIDE_NANOS = 1_000_000_000L;

    /** Last place-to-appear round trip the placement observer measured, or -1 when unknown. */
    private static volatile long measuredLatencyMillis = -1L;

    private static int refused;
    /** From the hit to the server's removal, the last time it was seen, or -1. */
    private static volatile long breakLatencyMillis = -1L;

    private CrystalBreakPrediction() {
    }

    public static void markBroken(EndCrystal crystal, long nowNanos) {
        if (!CrystalCore.active()) return;
        if (HIDDEN.size() >= MAX_TRACKED) {
            cleanup(nowNanos);
            if (HIDDEN.size() >= MAX_TRACKED) {
                return;
            }
        }
        HIDDEN.put(crystal.getId(), nowNanos + predictionWindowNanos());
        PREDICTED.put(crystal.getId(), nowNanos);
        CrystalBenchmark.onPredictedHidden(crystal.getId());
    }

    public static boolean isHidden(EndCrystal crystal) {
        // Called for every crystal in view, every frame. Almost always nothing is hidden, so this
        // check keeps the common case to one volatile read instead of a boxed map lookup.
        if (HIDDEN.isEmpty() || !CrystalCore.active()) {
            return false;
        }
        Long deadline = HIDDEN.get(crystal.getId());
        // An expired mark is left for the tick, which tells a refused hit from a confirmed one.
        return deadline != null && System.nanoTime() < deadline;
    }

    /** Whether the player's own hit is hiding this crystal right now, waiting for the server. */
    public static boolean isPredicted(int entityId) {
        if (!PREDICTED.containsKey(entityId)) {
            return false;
        }
        Long deadline = HIDDEN.get(entityId);
        return deadline != null && System.nanoTime() < deadline;
    }

    /**
     * Reinforces the prediction on a slow connection.
     *
     * <p>A fixed window is wrong at both ends. On a quick connection it keeps a refused hit hidden
     * long after the server has spoken; on a slow one it expires mid-flight, so the crystal
     * reappears and then vanishes again when the confirmation finally lands. Following the measured
     * round trip removes both.</p>
     *
     * <p>This only changes how long something goes drawn or undrawn. It does not touch an entity,
     * the crosshair, any cooldown, or anything that reaches the network. The ghost placements use
     * the same window, because they are covering the same round trip.</p>
     */
    public static long predictionWindowNanos() {
        long latency = currentLatencyMillis();
        if (latency < REINFORCE_ABOVE_MILLIS) {
            return BASE_HIDE_NANOS;
        }

        long scaled = (latency * 2L + 150L) * 1_000_000L;
        return Math.min(MAX_HIDE_NANOS, Math.max(BASE_HIDE_NANOS, scaled));
    }

    /**
     * Prefers the round trip the mod measured itself, because that is the delay actually being
     * covered. The server-reported ping is only a fallback for the first hits after joining, before
     * any placement has completed.
     */
    private static long currentLatencyMillis() {
        long measured = measuredLatencyMillis;
        if (measured >= 0L) {
            return measured;
        }
        return reportedPingMillis();
    }

    private static long reportedPingMillis() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0L;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return 0L;
        }
        UUID id = minecraft.player.getUUID();
        PlayerInfo info = connection.getPlayerInfo(id);
        return info == null ? 0L : Math.max(0L, info.getLatency());
    }

    /** Fed by the placement observer whenever a placement round trip completes. */
    public static void reportMeasuredLatency(long millis) {
        if (!CrystalCore.active()) return;
        if (millis >= 0L) {
            measuredLatencyMillis = millis;
        }
    }

    /** The round trip the windows follow: measured, or the server's ping until a placement completes. */
    public static long latencyMillis() {
        return currentLatencyMillis();
    }

    /** Called when the server actually removes the entity, so the mark is no longer needed. */
    public static void forget(Entity entity) {
        if (entity instanceof EndCrystal) {
            int id = entity.getId();
            HIDDEN.remove(id);
            removedAt(id, System.nanoTime());
            CRYSTALS.remove(id);
            FastPlacement.removed(id);
        }
    }

    /** A crystal entered the client world: its id is known to the removal reader. */
    public static void track(Entity entity) {
        if (entity instanceof EndCrystal) {
            CRYSTALS.add(entity.getId());
        }
    }

    /**
     * The removal reader. Runs on the network thread the moment the server's removal arrives, before
     * the client thread gets to it: a crystal the server has removed, anyone's, stops being drawn at
     * once instead of on the frame after next. The entity itself is left to the client thread, which
     * removes it as Vanilla does; this only marks it undrawn, the same mark a predicted break uses.
     */
    public static void serverRemoving(it.unimi.dsi.fastutil.ints.IntList ids) {
        if (CRYSTALS.isEmpty() || !CrystalCore.active()) {
            return;
        }
        long deadline = System.nanoTime() + REMOVAL_HIDE_NANOS;
        for (int index = 0; index < ids.size(); index++) {
            int id = ids.getInt(index);
            if (CRYSTALS.contains(id) && HIDDEN.size() < MAX_TRACKED * 4) {
                HIDDEN.merge(id, deadline, Math::max);
                // The server removed it: whatever the prediction said, this hit was not refused.
                removedAt(id, deadline - REMOVAL_HIDE_NANOS);
            }
        }
    }

    /**
     * Each client tick. A hidden mark past its window is dropped; when it was the player's own hit
     * and the crystal is still in the world, the server kept it: it is drawn again, with the cue.
     */
    public static void cleanup(long nowNanos) {
        if (HIDDEN.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Long> entry : HIDDEN.entrySet()) {
            if (nowNanos < entry.getValue()) {
                continue;
            }
            int id = entry.getKey();
            HIDDEN.remove(id);
            if (PREDICTED.remove(id) != null) {
                refusedHit(id);
            }
        }
    }

    private static void refusedHit(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getEntity(entityId) instanceof EndCrystal crystal) || crystal.isRemoved()) {
            return;
        }
        refused++;
        if (CrystalVisualConfig.refusedHitCue()) {
            level.playLocalSound(crystal.getX(), crystal.getY(), crystal.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.8F, 0.6F, false);
        }
    }

    /** The server removed a crystal: when it was the player's hit, the time from the hit to here is measured. */
    private static void removedAt(int id, long now) {
        Long hitAt = PREDICTED.remove(id);
        if (hitAt != null) {
            breakLatencyMillis = Math.max(0L, (now - hitAt) / 1_000_000L);
        }
    }

    /** From your hit to the server removing the crystal, the last time, in milliseconds; -1 before the first. */
    public static long breakLatencyMillis() {
        return breakLatencyMillis;
    }

    /** Hits the server kept, since the game started: each one came back on screen with the cue. */
    public static int refused() {
        return refused;
    }

    public static void reset() {
        HIDDEN.clear();
        PREDICTED.clear();
        CRYSTALS.clear();
        measuredLatencyMillis = -1L;
        InstantExplosion.reset();
        FastPlacement.reset();
    }
}
