package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import com.betterrtp.util.Threads;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Adds at most one cache entry per world per run, trying a few candidates strictly one after another. */
final class CacheFiller {
    private static final int MAX_CANDIDATES_PER_RUN = 4;
    private final BetterRTPPlugin plugin;
    private final LocationCache cache;
    private final Set<String> inFlight = new HashSet<>();

    CacheFiller(BetterRTPPlugin plugin, LocationCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    void fill(World world, WorldRtpSettings s, RtpConfig cfg, SafeLocationFinder finder) {
        String name = world.getName();
        if (cache.size(name) >= cache.capacity() || !inFlight.add(name)) return;
        attempt(name, s, cfg, finder, MAX_CANDIDATES_PER_RUN);
    }

    private void attempt(String name, WorldRtpSettings s, RtpConfig cfg, SafeLocationFinder finder, int left) {
        World w = Bukkit.getWorld(name);
        if (w == null || left <= 0) { inFlight.remove(name); return; }
        Bounds b = finder.bounds(w, s);
        if (b.isEmpty()) { inFlight.remove(name); return; }
        RandomCandidateGenerator.Candidate c = RandomCandidateGenerator.next(b, ThreadLocalRandom.current());
        w.getChunkAtAsync(c.chunkX(), c.chunkZ(), cfg.preloadChunks()).whenComplete((chunk, err) ->
                Threads.main(plugin, () -> {
                    if (!plugin.isEnabled()) { inFlight.remove(name); return; }
                    try {
                        World w2 = Bukkit.getWorld(name);
                        if (err == null && chunk != null && w2 != null
                                && finder.evaluate(w2, c.x(), c.z(), s, ThreadLocalRandom.current()).isPresent()) {
                            cache.offer(name, c.x(), c.z());
                            inFlight.remove(name);
                            return;
                        }
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Unexpected error while refilling the RTP cache", e);
                        inFlight.remove(name);
                        return;
                    }
                    attempt(name, s, cfg, finder, left - 1);
                }));
    }
}
