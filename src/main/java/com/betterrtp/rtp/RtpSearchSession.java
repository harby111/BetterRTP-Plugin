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
    private final long deadlineNanos;
    private final boolean generate;
    private final boolean earlySearch;
    private int attempts;
    private long step;
    private boolean done;
    private BukkitTask pending;
    private BukkitTask timeout;

    RtpSearchSession(BetterRTPPlugin plugin, RtpService service, RtpRequest request, WorldRtpSettings settings,
                     RtpConfig cfg, SafeLocationFinder finder, LocationCache cache, NamespacedKey biome, boolean earlySearch) {
        this.plugin = plugin;
        this.service = service;
        this.request = request;
        this.settings = settings;
        this.cfg = cfg;
        this.finder = finder;
        this.cache = cache;
        this.biome = biome;
        this.maxAttempts = biome == null ? cfg.safeAttempts() : AreaMath.biomeAttempts(cfg.safeAttempts(), cfg.biome().attemptsMultiplier());
        this.deadlineNanos = System.nanoTime() + cfg.searchTimeoutSeconds() * 1_000_000_000L;
        this.generate = cfg.preloadChunks() && (biome == null || cfg.biome().generateChunks());
        this.earlySearch = earlySearch;
    }

    void start() { runNext(); }

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
        if (request.state() != RtpState.SEARCHING && request.state() != RtpState.EARLY_SEARCH) { abort(); return; }
        if (attempts >= maxAttempts || System.nanoTime() - deadlineNanos >= 0) { fail(Msg.SEARCH_FAILED); return; }
        
        World world = Bukkit.getWorld(request.worldName());
        if (world == null) { fail(Msg.WORLD_UNAVAILABLE); return; }

        OptionalLong cached = cache.poll(request.worldName());
        if (cached.isPresent()) {
            int x = LocationCache.unpackX(cached.getAsLong());
            int z = LocationCache.unpackZ(cached.getAsLong());
            evaluateSync(world, x, z);
            return;
        }

        int[] bounds = {0,0,0,0};
        int x = 0, z = 0;
        if (biome == null) {
            Bounds b = finder.bounds(world, settings);
            x = ThreadLocalRandom.current().nextInt(b.minX(), b.maxX() + 1);
            z = ThreadLocalRandom.current().nextInt(b.minZ(), b.maxZ() + 1);
        } else {
            x = ThreadLocalRandom.current().nextInt(-10000, 10000);
            z = ThreadLocalRandom.current().nextInt(-10000, 10000);
        }
        
        final long myStep = ++step;
        attempts++;
        
        if (generate) {
            CompletableFuture<Chunk> fut = world.getChunkAtAsync(x >> 4, z >> 4, true);
            long timeoutMs = cfg.performance().chunkLoadTimeoutMs();
            if (timeout != null) timeout.cancel();
            timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (myStep == step && !done) fut.cancel(true);
            }, (timeoutMs + 50) / 50);
            
            fut.whenComplete((chunk, err) -> {
                if (err != null || chunk == null) {
                    Threads.main(plugin, () -> { if (myStep == step && !done) fail(Msg.SEARCH_FAILED); });
                    return;
                }
                Threads.main(plugin, () -> {
                    if (myStep != step || done) return;
                    if (timeout != null) { timeout.cancel(); timeout = null; }
                    evaluateSync(world, x, z);
                });
            });
        } else {
            Chunk c = world.getChunkAt(x >> 4, z >> 4, false);
            if (c == null || !c.isLoaded()) {
                long delayTicks = service.candidateDelayTicksForRequest(request);
                pending = Bukkit.getScheduler().runTaskLater(plugin, this::runNext, delayTicks);
                return;
            }
            evaluateSync(world, x, z);
        }
    }

    private void evaluateSync(World world, int x, int z) {
        if (done) return;
        if (biome != null) {
            var bio = world.getBiome(x, 64, z);
            if (!biome.equals(bio.getKey())) {
                long delayTicks = service.candidateDelayTicksForRequest(request);
                pending = Bukkit.getScheduler().runTaskLater(plugin, this::runNext, delayTicks);
                return;
            }
        }
        finder.evaluate(world, x, z, settings, ThreadLocalRandom.current()).ifPresent(spot -> {
            if (earlySearch) {
                service.onEarlySearchSuccess(request, world, spot);
            } else {
                service.onSearchSuccess(request, world, spot);
            }
            return;
        });
        if (done) return;
        long delayTicks = service.candidateDelayTicksForRequest(request);
        pending = Bukkit.getScheduler().runTaskLater(plugin, this::runNext, delayTicks);
    }
}