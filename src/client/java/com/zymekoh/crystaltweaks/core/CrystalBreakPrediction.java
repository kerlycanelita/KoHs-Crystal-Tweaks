package com.zymekoh.crystaltweaks.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
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
 * Hiding avoids both: a prediction the server refuses simply becomes visible again.</p>
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

    /** Last place-to-appear round trip the placement observer measured, or -1 when unknown. */
    private static volatile long measuredLatencyMillis = -1L;

    private CrystalBreakPrediction() {
    }

    public static void markBroken(EndCrystal crystal, long nowNanos) {
        if (!CrystalOptimizerGuard.optimizationsAllowed()) return;
        if (HIDDEN.size() >= MAX_TRACKED) {
            cleanup(nowNanos);
            if (HIDDEN.size() >= MAX_TRACKED) {
                return;
            }
        }
        HIDDEN.put(crystal.getId(), nowNanos + predictionWindowNanos());
    }

    public static boolean isHidden(EndCrystal crystal) {
        if (!CrystalOptimizerGuard.optimizationsAllowed()) return false;
        // Called for every crystal in view, every frame. Almost always nothing is hidden, so this
        // check keeps the common case to one volatile read instead of a boxed map lookup.
        if (HIDDEN.isEmpty()) {
            return false;
        }

        Long deadline = HIDDEN.get(crystal.getId());
        if (deadline == null) {
            return false;
        }
        if (System.nanoTime() >= deadline) {
            HIDDEN.remove(crystal.getId());
            return false;
        }
        return true;
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
        if (!CrystalOptimizerGuard.optimizationsAllowed()) return;
        if (millis >= 0L) {
            measuredLatencyMillis = millis;
        }
    }

    /** Called when the server actually removes the entity, so the mark is no longer needed. */
    public static void forget(Entity entity) {
        if (entity instanceof EndCrystal) {
            HIDDEN.remove(entity.getId());
        }
    }

    public static void cleanup(long nowNanos) {
        if (HIDDEN.isEmpty()) {
            return;
        }
        HIDDEN.values().removeIf(deadline -> nowNanos >= deadline);
    }

    public static void reset() {
        HIDDEN.clear();
        measuredLatencyMillis = -1L;
    }
}
