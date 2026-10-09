package com.betterrtp.rtp;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Optional, tiny cache of candidate X/Z pairs per world (packed into a long: no Location/Chunk references).
 * Entries are only a hint: every use is fully re-validated by the normal search.
 */
public final class LocationCache {
    private final Map<String, ArrayDeque<Long>> perWorld = new HashMap<>();
    private int capacity;

    public void configure(int capacity) { this.capacity = Math.max(0, capacity); }
    public int capacity() { return capacity; }
    public boolean enabled() { return capacity > 0; }

    public void offer(String world, int x, int z) {
        if (capacity <= 0) return;
        ArrayDeque<Long> dq = perWorld.computeIfAbsent(world, k -> new ArrayDeque<>());
        if (dq.size() < capacity) dq.addLast(pack(x, z));
    }

    public OptionalLong poll(String world) {
        ArrayDeque<Long> dq = perWorld.get(world);
        if (dq == null || dq.isEmpty()) return OptionalLong.empty();
        return OptionalLong.of(dq.pollFirst());
    }

    public int size(String world) {
        ArrayDeque<Long> dq = perWorld.get(world);
        return dq == null ? 0 : dq.size();
    }

    public void clear(String world) { perWorld.remove(world); }
    public void clear() { perWorld.clear(); }

    public static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static int unpackX(long v) { return (int) (v >> 32); }
    public static int unpackZ(long v) { return (int) v; }
}
