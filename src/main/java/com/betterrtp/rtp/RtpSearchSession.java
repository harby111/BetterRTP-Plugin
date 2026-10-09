package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.Msg;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import com.betterrtp.util.Threads;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.OptionalLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Bounded, strictly sequential search for ONE request: one candidate (one chunk load) in flight at a time.
 * Everything runs on the main thread; the chunk callback is hopped back to it. A step token invalidates
 * late callbacks (timeout, cancellation, abort) so they can never act on stale state.
 */
final class RtpSearchSession {
    private final BetterRTPPlugin plugin;
    private final RtpService service;
    private final RtpRequest request;
    private final WorldRtpSettings settings;
    private final RtpConfig cfg;
    private final SafeLocationFinder finder;
    private final LocationCache cache;
    private final NamespacedKey biome;
    private final int maxAttempts;
    private long deadlineNanos;
    private boolean timed;           // false while searching during the countdown (no time limit yet)
    private final boolean generate;
    private int attempts;
    private long step;
    private boolean done;
    private BukkitTask pending;
    private BukkitTask timeout;

    RtpSearchSession(BetterRTPPlugin plugin, RtpService service, RtpRequest request, WorldRtpSettings settings,
                     RtpConfig cfg, SafeLocationFinder finder, LocationCache cache, NamespacedKey biome) {
        this.plugin = plugin;
        this.service = service;
        this.request = request;
        this.settings = settings;
        this.cfg = cfg;
        this.finder = finder;
        this.cache = cache;
        this.biome = biome;
        this.maxAttempts = biome == null ? cfg.safeAttempts() : AreaMath.biomeAttempts(cfg.safeAttempts(), cfg.biome().attemptsMultiplier());
        // During the countdown only the attempt limit applies; the time limit starts when the search goes full speed.
        this.timed = request.state() != RtpState.COUNTDOWN;
        this.deadlineNanos = System.nanoTime() + cfg.searchTimeoutSeconds() * 1_000_000_000L;
        this.generate = cfg.preloadChunks() && (biome == null || cfg.biome().generateChunks());
    }

    void start() { runNext(); }

    /**
     * Called when the countdown ended while this search is still running: starts the time limit and,
     * if the session is just waiting between two candidates, evaluates the next one immediately.
     */
    void speedUp() {
        if (done) return;
        timed = true;
        deadlineNanos = System.nanoTime() + cfg.searchTimeoutSeconds() * 1_000_000_000L;
        if (pending != null) {
            pending.cancel();
            pending = null;
            runNext();
        }
    }

    void abort() {
        done = true;
        step++;
        cancelTasks();
    }

    private void cancelTasks() {
        if (pending != null) { pending.cancel(); pending = null; }
        if (timeout != null) { timeout.cancel(); timeout = null; }
    }

    private void fail(Msg reason) {
        if (done) return;
        done = true;
        cancelTasks();
        service.onSearchFailed(request, reason);
    }

    private void runNext() {
        pending = null;
        if (done) return;
        if (!request.searchAllowed()) { abort(); return; }
        if (attempts >= maxAttempts || (timed && System.nanoTime() - deadlineNanos >= 0)) { fail(Msg.SEARCH_FAILED); return; }
        World world = Bukkit.getWorld(request.worldName());
        if (world == null) { fail(Msg.WORLD_UNAVAILABLE); return; }
        Bounds bounds = finder.bounds(world, settings);
        if (bounds.isEmpty()) { fail(Msg.CONFIG_ERROR); return; }

        int x, z;
        OptionalLong cached = biome == null ? cache.poll(world.getName()) : OptionalLong.empty();
        if (cached.isPresent() && bounds.contains(LocationCache.unpackX(cached.getAsLong()), LocationCache.unpackZ(cached.getAsLong()))) {
            x = LocationCache.unpackX(cached.getAsLong());
            z = LocationCache.unpackZ(cached.getAsLong());
        } else {
            RandomCandidateGenerator.Candidate c = RandomCandidateGenerator.next(bounds, ThreadLocalRandom.current());
            x = c.x();
            z = c.z();
        }
        attempts++;
        final long my = ++step;
        final int fx = x, fz = z;
        long timeoutTicks = Math.max(1L, cfg.performance().chunkLoadTimeoutMs() / 50L);
        timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!done && my == step) { step++; scheduleNext(); }
        }, timeoutTicks);
        CompletableFuture<Chunk> future;
        try {
            future = world.getChunkAtAsync(fx >> 4, fz >> 4, generate);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Chunk request failed", e);
            fail(Msg.SEARCH_FAILED);
            return;
        }
        future.whenComplete((chunk, err) -> Threads.main(plugin, () -> onChunk(my, fx, fz, chunk, err)));
    }

    private void onChunk(long my, int x, int z, Chunk chunk, Throwable err) {
        if (done || my != step) return;      // cancelled, timed out or superseded
        step++;
        if (timeout != null) { timeout.cancel(); timeout = null; }
        if (!request.searchAllowed()) { abort(); return; }
        if (err != null || chunk == null) { scheduleNext(); return; }
        World world = Bukkit.getWorld(request.worldName());
        if (world == null) { fail(Msg.WORLD_UNAVAILABLE); return; }
        try {
            if (finder.bounds(world, settings).contains(x, z)) {
                Optional<Spot> spot = finder.evaluate(world, x, z, settings, ThreadLocalRandom.current());
                if (spot.isPresent() && (biome == null || finder.biomeMatches(world, spot.get(), biome))) {
                    done = true;
                    cancelTasks();
                    service.onSearchSuccess(request, world, spot.get());
                    return;
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Unexpected error while evaluating an RTP candidate", e);
        }
        scheduleNext();
    }

    private void scheduleNext() {
        if (done) return;
        pending = Bukkit.getScheduler().runTaskLater(plugin, this::runNext, service.candidateDelayTicks(request));
    }
}