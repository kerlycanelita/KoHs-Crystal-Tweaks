package com.zymekoh.crystaltweaks.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded, visual-only lifetime queue shared by world and preview effects. */
public final class AfterglowTimeline<T> {
    public record Sample<T>(T value, float opacity, float progress) { }
    /** Each light keeps the duration it started with, so moving the slider never jumps a fade. */
    private record Entry<T>(T value, long started, long duration) { }
    private final Map<UUID, Entry<T>> entries = new LinkedHashMap<>();
    private final int capacity;

    public AfterglowTimeline(int capacity) { this.capacity = Math.max(1, capacity); }

    public void start(UUID id, T value, long now) {
        start(id, value, now, CrystalGlowMath.FADE_NANOS);
    }

    public void start(UUID id, T value, long now, long durationNanos) {
        expire(now);
        if (entries.containsKey(id)) return; // Repeated hide/unload callbacks do not prolong the light.
        while (entries.size() >= capacity) entries.remove(entries.keySet().iterator().next());
        entries.put(id, new Entry<>(value, now, Math.max(1L, durationNanos)));
    }

    public List<Sample<T>> samples(long now) {
        expire(now);
        List<Sample<T>> samples = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries.values()) {
            samples.add(new Sample<>(entry.value, CrystalGlowMath.fade(now - entry.started, entry.duration),
                    CrystalGlowMath.progress(now - entry.started, entry.duration)));
        }
        return samples;
    }

    public void cancel(UUID id) { entries.remove(id); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
    private void expire(long now) {
        entries.values().removeIf(entry -> now - entry.started >= entry.duration);
    }
}
