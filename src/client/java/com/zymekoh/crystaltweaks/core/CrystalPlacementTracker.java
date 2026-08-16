package com.zymekoh.crystaltweaks.core;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;

/**
 * Bounded local state for genuine Vanilla crystal placement attempts.
 *
 * <p>This tracker never invokes an interaction and never creates or sends a packet.</p>
 */
public final class CrystalPlacementTracker {
    private static final int MAX_PENDING = 32;
    private static final int MAX_SAMPLES = 32;
    private static final long ATTEMPT_TIMEOUT_NANOS = 3_000_000_000L;

    private final Map<BlockPos, Attempt> pending = new LinkedHashMap<>();
    private final ArrayDeque<Long> latencySamples = new ArrayDeque<>();
    private long lastLatencyNanos = -1L;

    public synchronized void record(BlockPos base, int sequence, long sentAtNanos) {
        BlockPos immutableBase = base.immutable();
        cleanup(sentAtNanos);
        pending.remove(immutableBase);
        pending.put(immutableBase, new Attempt(
                immutableBase,
                sequence,
                sentAtNanos,
                sentAtNanos + ATTEMPT_TIMEOUT_NANOS));

        while (pending.size() > MAX_PENDING) {
            Iterator<BlockPos> iterator = pending.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }

    public synchronized OptionalLong confirm(BlockPos base, long confirmedAtNanos) {
        cleanup(confirmedAtNanos);
        Attempt attempt = pending.remove(base);
        if (attempt == null) {
            return OptionalLong.empty();
        }

        long latency = Math.max(0L, confirmedAtNanos - attempt.sentAtNanos());
        lastLatencyNanos = latency;
        latencySamples.addLast(latency);
        while (latencySamples.size() > MAX_SAMPLES) {
            latencySamples.removeFirst();
        }
        return OptionalLong.of(latency);
    }

    public synchronized void cleanup(long nowNanos) {
        pending.values().removeIf(attempt -> nowNanos >= attempt.expiresAtNanos());
    }

    public synchronized void reset() {
        pending.clear();
        latencySamples.clear();
        lastLatencyNanos = -1L;
    }

    public synchronized long lastLatencyMillis() {
        return lastLatencyNanos < 0L ? -1L : Math.round(lastLatencyNanos / 1_000_000.0D);
    }

    public synchronized long averageLatencyMillis() {
        if (latencySamples.isEmpty()) {
            return -1L;
        }
        long total = 0L;
        for (long latency : latencySamples) {
            total += latency;
        }
        return Math.round(total / (double) latencySamples.size() / 1_000_000.0D);
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private record Attempt(
            BlockPos base,
            int sequence,
            long sentAtNanos,
            long expiresAtNanos
    ) {
    }
}
