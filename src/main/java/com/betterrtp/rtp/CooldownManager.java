package com.betterrtp.rtp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** In-memory per-player cooldowns. Expired entries are dropped lazily and on every new application. */
public final class CooldownManager {
    private final LongSupplier clockMillis;
    private final Map<UUID, Long> expiry = new HashMap<>();

    public CooldownManager() { this(System::currentTimeMillis); }
    public CooldownManager(LongSupplier clockMillis) { this.clockMillis = clockMillis; }

    public void apply(UUID id, int seconds) {
        purgeExpired();
        if (seconds <= 0) { expiry.remove(id); return; }
        expiry.put(id, clockMillis.getAsLong() + seconds * 1000L);
    }

    /** Remaining whole seconds (rounded up); 0 when none. */
    public long remainingSeconds(UUID id) {
        Long e = expiry.get(id);
        if (e == null) return 0;
        long left = e - clockMillis.getAsLong();
        if (left <= 0) { expiry.remove(id); return 0; }
        return (left + 999) / 1000;
    }

    /** On quit: forget expired entries only (an active cooldown must survive a relog). */
    public void onQuit(UUID id) { remainingSeconds(id); }

    public void purgeExpired() {
        long now = clockMillis.getAsLong();
        expiry.values().removeIf(e -> e <= now);
    }

    public void clear() { expiry.clear(); }
    public int size() { return expiry.size(); }

    public static String format(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60, s = seconds % 60;
        return s == 0 ? m + "m" : m + "m " + s + "s";
    }
}
