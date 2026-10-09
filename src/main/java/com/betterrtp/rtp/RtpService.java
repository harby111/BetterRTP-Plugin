package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.ConfigManager;
import com.betterrtp.config.Messages;
import com.betterrtp.config.Msg;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import com.betterrtp.effect.RtpEffects;
import com.betterrtp.gui.BedrockRtpGui;
import com.betterrtp.gui.JavaRtpGui;
import com.betterrtp.listener.RtpMovementListener;
import com.betterrtp.util.Threads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.logging.Level;

/**
 * Orchestrates every RTP request: validation, countdown, bounded search with global backpressure,
 * final teleport, cooldown. Main-thread only. A request can never teleport after it was cancelled
 * because every continuation re-checks the request's state machine.
 */
public final class RtpService {
    public static final String PERM_USE = "betterrtp.use";
    public static final String PERM_BYPASS = "betterrtp.bypass.cooldown";

    /** Makes the Nether scan start at the top of the allowed range (deterministic re-validation). */
    private static final RandomGenerator TOP_DOWN = new RandomGenerator() {
        @Override public long nextLong() { return 0L; }
        @Override public int nextInt(int bound) { return bound - 1; }
    };

    private final BetterRTPPlugin plugin;
    private final ConfigManager cm;
    private final Map<UUID, RtpRequest> active = new HashMap<>();
    private final Map<UUID, RtpSearchSession> sessions = new HashMap<>();
    private final ArrayDeque<RtpRequest> queue = new ArrayDeque<>();
    private final CooldownManager cooldowns = new CooldownManager();
    private final WorldResolver resolver = new WorldResolver();
    private final LocationCache cache = new LocationCache();
    private final CacheFiller filler;
    private final CountdownManager countdowns;
    private final RtpMovementListener movementListener;
    private RtpEffects effects;
    private SafeLocationFinder finder;
    private JavaRtpGui javaGui;
    private BedrockRtpGui bedrockGui;
    private BukkitTask refillTask;
    private boolean movementRegistered;
    private long nextId;
    private int activeSearches;

    public RtpService(BetterRTPPlugin plugin, ConfigManager cm) {
        this.plugin = plugin;
        this.cm = cm;
        this.filler = new CacheFiller(plugin, cache);
        this.countdowns = new CountdownManager(plugin, this::tickCountdown);
        this.movementListener = new RtpMovementListener(this, cm);
        rebuild();
    }

    // ------------------------------------------------------------------ lifecycle

    /** (Re)builds everything derived from the configuration. Active requests must be cancelled first. */
    private void rebuild() {
        RtpConfig cfg = cm.config();
        Messages msgs = cm.messages();
        if (effects != null) effects.shutdown();
        effects = new RtpEffects(cfg, msgs);
        finder = new SafeLocationFinder(new SafetyChecker(cfg.safety(), cfg.blacklist(), new HazardCatalog()));
        javaGui = new JavaRtpGui(this, cm);
        bedrockGui = new BedrockRtpGui(plugin, this, cm);
        cache.configure(cfg.cache().enabled() ? cfg.cache().size() : 0);
        cache.clear();
        if (refillTask != null) { refillTask.cancel(); refillTask = null; }
        if (cache.enabled()) {
            long iv = cfg.cache().refillIntervalTicks();
            refillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refillTick, iv, iv);
        }
    }

    public void applyConfig() {
        cancelAll(Msg.CANCELLED_RELOAD);
        rebuild();
    }

    public void shutdown() {
        for (RtpRequest r : new ArrayList<>(active.values())) {
            r.abort();
            finishRequest(r);
        }
        countdowns.clear();
        if (refillTask != null) { refillTask.cancel(); refillTask = null; }
        effects.shutdown();
        cache.clear();
    }

    // ------------------------------------------------------------------ public entry points

    public RtpRequest activeFor(UUID id) { return active.get(id); }
    public WorldResolver resolver() { return resolver; }
    public RtpConfig config() { return cm.config(); }
    public Messages messages() { return cm.messages(); }
    public CooldownManager cooldowns() { return cooldowns; }

    /** Opens the Bedrock form or Java inventory. {@code force} ignores open-on-rtp (used by /rtpgui). */
    public void openMenu(Player p, boolean force) {
        RtpConfig cfg = cm.config();
        if (!p.hasPermission(PERM_USE)) { messages().send(p, Msg.NO_PERMISSION); return; }
        if (!force && !cfg.openOnRtp()) { startDestination(p, Destination.OVERWORLD); return; }
        if (active.containsKey(p.getUniqueId())) { messages().send(p, Msg.ALREADY_IN_PROGRESS); return; }
        if (cooldownBlocks(p)) return;
        if (cfg.bedrockGui().enabled() && plugin.bedrock().isBedrock(p.getUniqueId())) {
            if (bedrockGui.open(p)) return;
            messages().send(p, Msg.BEDROCK_UNAVAILABLE);
        }
        javaGui.open(p);
    }

    public void startDestination(Player p, Destination d) {
        RtpConfig cfg = cm.config();
        var resolved = resolver.resolve(d, cfg);
        if (resolved.isEmpty()) {
            messages().send(p, Msg.DESTINATION_UNAVAILABLE, Messages.p("destination", cfg.destinationNames().get(d)));
            return;
        }
        begin(p, resolved.get().world(), resolved.get().settings(), cfg.destinationNames().get(d), null);
    }

    public void startWorld(Player p, String worldName) {
        RtpConfig cfg = cm.config();
        WorldResolver.Lookup l = resolver.byName(worldName, cfg);
        switch (l.status()) {
            case NOT_FOUND -> messages().send(p, Msg.INVALID_WORLD, Messages.p("world", worldName));
            case DISABLED -> messages().send(p, Msg.WORLD_DISABLED, Messages.p("world", worldName));
            case OK -> begin(p, l.resolved().world(), l.resolved().settings(), l.resolved().world().getName(), null);
        }
    }

    public void startBiome(Player p, NamespacedKey biome) {
        RtpConfig cfg = cm.config();
        if (!cfg.biome().enabled()) { messages().send(p, Msg.BIOME_DISABLED); return; }
        // Current world if it is an enabled RTP world, otherwise the resolved Overworld.
        var current = resolver.settingsFor(p.getWorld().getName(), cfg).filter(WorldRtpSettings::enabled);
        if (current.isPresent()) {
            begin(p, p.getWorld(), current.get(), biome.getKey(), biome);
            return;
        }
        var ow = resolver.resolve(Destination.OVERWORLD, cfg);
        if (ow.isEmpty()) {
            messages().send(p, Msg.DESTINATION_UNAVAILABLE, Messages.p("destination", cfg.destinationNames().get(Destination.OVERWORLD)));
            return;
        }
        begin(p, ow.get().world(), ow.get().settings(), biome.getKey(), biome);
    }

    // ------------------------------------------------------------------ request start

    private boolean cooldownBlocks(Player p) {
        if (p.hasPermission(PERM_BYPASS)) return false;
        long rem = cooldowns.remainingSeconds(p.getUniqueId());
        if (rem <= 0) return false;
        messages().send(p, Msg.COOLDOWN, Messages.p("time", CooldownManager.format(rem)), Messages.p("seconds", rem));
        return true;
    }

    private void begin(Player p, World world, WorldRtpSettings settings, String label, NamespacedKey biome) {
        UUID id = p.getUniqueId();
        if (!p.hasPermission(PERM_USE)) { messages().send(p, Msg.NO_PERMISSION); return; }
        if (active.containsKey(id)) { messages().send(p, Msg.ALREADY_IN_PROGRESS); return; }
        if (cooldownBlocks(p)) return;
        RtpConfig cfg = cm.config();
        Location l = p.getLocation();
        RtpRequest r = new RtpRequest(++nextId, id, world.getName(), label, biome == null ? null : biome.toString(),
                l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), cfg.countdownSeconds(), settings.cooldownSeconds());
        active.put(id, r);
        if (!movementRegistered && cfg.cancelOnMove()) {
            Bukkit.getPluginManager().registerEvents(movementListener, plugin);
            movementRegistered = true;
        }
        countdowns.add(id);
        effects.showCountdown(p, r);
        // Last on purpose: a synchronous failure must not leave countdown visuals behind.
        if (cfg.performance().searchDuringCountdown()) startPreSearch(r, world, settings);
    }

    /** Starts a slow search that runs together with the countdown (only if a search slot is free). */
    private void startPreSearch(RtpRequest r, World world, WorldRtpSettings settings) {
        if (r.state() != RtpState.COUNTDOWN) return;
        if (activeSearches >= cm.config().performance().maxGlobalSearches()) return;   // falls back to the classic flow
        r.enablePreSearch();
        launchSession(r, world, settings);
    }

    // ------------------------------------------------------------------ countdown

    private void tickCountdown(UUID id) {
        RtpRequest r = active.get(id);
        if (r == null || r.state() != RtpState.COUNTDOWN) { countdowns.remove(id); return; }
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline() || p.isDead()) { r.abort(); finishRequest(r); return; }
        if (r.decrement() > 0) { effects.showCountdown(p, r); return; }
        countdowns.remove(id);
        effects.clear(id);
        effects.clearActionbar(p);
        onCountdownDone(p, r);
    }

    private void onCountdownDone(Player p, RtpRequest r) {
        RtpConfig cfg = cm.config();
        World world = Bukkit.getWorld(r.worldName());
        var settings = resolver.settingsFor(r.worldName(), cfg).filter(WorldRtpSettings::enabled);
        if (world == null) { fail(r, Msg.WORLD_UNAVAILABLE); return; }
        if (settings.isEmpty()) { fail(r, Msg.WORLD_DISABLED); return; }

        // 1) A safe spot was already found during the countdown: re-validate it and teleport right away.
        Spot early = r.foundSpot();
        if (early != null) {
            Spot use = revalidate(world, settings.get(), r, early);
            if (use != null) {
                if (!r.moveTo(RtpState.SEARCHING)) return;
                onSearchSuccess(r, world, use);
                return;
            }
            releaseHeldChunk(r);          // the spot is no longer safe: search again at full speed
            r.clearFoundSpot();
        }

        // 2) The search started with the countdown is still running: switch it to full speed.
        RtpSearchSession running = sessions.get(r.playerId());
        if (running != null) {
            if (!r.moveTo(RtpState.SEARCHING)) return;
            messages().send(p, Msg.SEARCHING);
            running.speedUp();
            return;
        }

        // 3) Classic flow: search after the countdown.
        if (activeSearches < cfg.performance().maxGlobalSearches()) {
            startSearch(r, world, settings.get());
        } else if (queue.size() < cfg.performance().maxQueueSize()) {
            r.moveTo(RtpState.QUEUED);
            r.markQueued(System.nanoTime());
            queue.addLast(r);
            messages().send(p, Msg.QUEUED);
        } else {
            fail(r, Msg.BUSY);
        }
    }

    // ------------------------------------------------------------------ search

    private void startSearch(RtpRequest r, World world, WorldRtpSettings settings) {
        if (!r.moveTo(RtpState.SEARCHING)) return;
        Player p = Bukkit.getPlayer(r.playerId());
        if (p == null) { r.abort(); finishRequest(r); return; }
        messages().send(p, Msg.SEARCHING);
        launchSession(r, world, settings);
    }

    /** Creates and starts a search session and takes one global search slot. */
    private void launchSession(RtpRequest r, World world, WorldRtpSettings settings) {
        NamespacedKey biome = r.biomeKey() == null ? null : NamespacedKey.fromString(r.biomeKey());
        RtpSearchSession s = new RtpSearchSession(plugin, this, r, settings, cm.config(), finder, cache, biome);
        sessions.put(r.playerId(), s);
        activeSearches++;
        s.start();
    }

    /**
     * Cheap safety re-check of a spot found earlier during the countdown. If its chunk is not loaded
     * the spot is trusted (teleportAsync loads it). Returns null when the spot is no longer valid.
     */
    private Spot revalidate(World world, WorldRtpSettings settings, RtpRequest r, Spot spot) {
        if (!world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) return spot;
        try {
            if (!finder.bounds(world, settings).contains(spot.x(), spot.z())) return null;
            Optional<Spot> again = finder.evaluate(world, spot.x(), spot.z(), settings, TOP_DOWN);
            if (again.isEmpty()) return null;
            if (r.biomeKey() != null) {
                NamespacedKey wanted = NamespacedKey.fromString(r.biomeKey());
                if (wanted != null && !finder.biomeMatches(world, again.get(), wanted)) return null;
            }
            return again.get();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Unexpected error while re-validating an RTP spot", e);
            return null;
        }
    }

    /** Removes the chunk ticket that kept the pre-found spot loaded (idempotent). */
    private void releaseHeldChunk(RtpRequest r) {
        if (!r.chunkTicket()) return;
        r.setChunkTicket(false);
        Spot s = r.foundSpot();
        World w = Bukkit.getWorld(r.worldName());
        if (s != null && w != null) w.removePluginChunkTicket(s.x() >> 4, s.z() >> 4, plugin);
    }

    /** Frees the search slot of a request (idempotent) and promotes queued requests. */
    private void releaseSearch(RtpRequest r) {
        RtpSearchSession s = sessions.remove(r.playerId());
        if (s != null) {
            s.abort();
            activeSearches = Math.max(0, activeSearches - 1);
        }
        queue.remove(r);
        pump();
    }

    private void pump() {
        RtpConfig cfg = cm.config();
        long maxWait = cfg.searchTimeoutSeconds() * 1_000_000_000L;
        while (activeSearches < cfg.performance().maxGlobalSearches() && !queue.isEmpty()) {
            RtpRequest r = queue.pollFirst();
            if (r.state() != RtpState.QUEUED) continue;
            if (System.nanoTime() - r.queuedAtNanos() > maxWait) { fail(r, Msg.BUSY); continue; }
            World world = Bukkit.getWorld(r.worldName());
            var settings = resolver.settingsFor(r.worldName(), cfg).filter(WorldRtpSettings::enabled);
            if (world == null) { fail(r, Msg.WORLD_UNAVAILABLE); continue; }
            if (settings.isEmpty()) { fail(r, Msg.WORLD_DISABLED); continue; }
            startSearch(r, world, settings.get());
        }
    }

    /** Delay between candidates so the whole server stays under max-candidates-per-second. */
    int candidateDelayTicks(RtpRequest r) {
        RtpConfig.Performance perf = cm.config().performance();
        double ticks = 20.0 * Math.max(1, activeSearches) / perf.maxCandidatesPerSecond();
        int base = (int) Math.max(1, Math.min(40, Math.round(ticks)));
        // While the countdown is still running the search is deliberately slower.
        if (r.state() == RtpState.COUNTDOWN) return Math.min(200, base * perf.countdownSearchSlowdown());
        return base;
    }

    void onSearchFailed(RtpRequest r, Msg reason) {
        fail(r, reason);
    }

    void onSearchSuccess(RtpRequest r, World world, Spot spot) {
        Player p = Bukkit.getPlayer(r.playerId());
        if (p == null || !p.isOnline() || p.isDead()) { r.abort(); finishRequest(r); return; }
        if (r.state() == RtpState.COUNTDOWN) {
            // Found early: remember it and wait for the countdown to finish.
            r.setFoundSpot(spot);
            try {
                if (world.addPluginChunkTicket(spot.x() >> 4, spot.z() >> 4, plugin)) r.setChunkTicket(true);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not hold the chunk of a pre-found RTP spot", e);
            }
            if (cm.config().debug()) {
                plugin.getLogger().info("RTP " + p.getName() + " pre-found " + world.getName() + " " + spot + " during countdown");
            }
            releaseSearch(r);
            return;
        }
        if (!r.moveTo(RtpState.TELEPORTING)) { finishRequest(r); return; }   // cancelled in the meantime
        releaseSearch(r);
        if (cm.config().debug()) {
            plugin.getLogger().info("RTP " + p.getName() + " -> " + world.getName() + " " + spot);
        }
        Location dest = new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());
        try {
            p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((ok, err) ->
                    Threads.main(plugin, () -> afterTeleport(r, dest, Boolean.TRUE.equals(ok) && err == null, err)));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "teleportAsync threw", e);
            afterTeleport(r, dest, false, e);
        }
    }

    private void afterTeleport(RtpRequest r, Location dest, boolean success, Throwable err) {
        if (!r.moveTo(success ? RtpState.COMPLETED : RtpState.FAILED)) { finishRequest(r); return; }
        Player p = Bukkit.getPlayer(r.playerId());
        if (err != null) plugin.getLogger().log(Level.WARNING, "Teleport failed unexpectedly", err);
        if (p != null && p.isOnline()) {
            if (success) {
                if (!p.hasPermission(PERM_BYPASS)) cooldowns.apply(r.playerId(), r.cooldownSeconds());
                effects.playTeleport(p);
                messages().send(p, Msg.TELEPORT_SUCCESS, Messages.p("x", dest.getBlockX()), Messages.p("y", dest.getBlockY()),
                        Messages.p("z", dest.getBlockZ()), Messages.p("world", dest.getWorld().getName()));
            } else {
                messages().send(p, Msg.TELEPORT_FAILED);
            }
        }
        finishRequest(r);
    }

    // ------------------------------------------------------------------ cancel / fail / cleanup

    /** Cancels if still cancellable. @return true if it was cancelled. */
    public boolean cancel(UUID id, Msg reason) {
        RtpRequest r = active.get(id);
        if (r == null || !r.moveTo(RtpState.CANCELLED)) return false;
        Player p = Bukkit.getPlayer(id);
        finishRequest(r);
        if (p != null && reason != null) messages().send(p, reason);
        return true;
    }

    /** Silent hard stop (disconnect, death). Also stops requests whose teleport is in flight. */
    public void abort(UUID id) {
        RtpRequest r = active.get(id);
        if (r == null) return;
        r.abort();
        finishRequest(r);
    }

    private void fail(RtpRequest r, Msg reason) {
        if (!r.moveTo(RtpState.FAILED)) return;
        Player p = Bukkit.getPlayer(r.playerId());
        finishRequest(r);
        if (p != null && p.isOnline() && reason != null) {
            messages().send(p, reason, Messages.p("world", r.worldName()));
        }
    }

    public void cancelAll(Msg reason) {
        for (RtpRequest r : new ArrayList<>(active.values())) {
            if (r.cancellable()) cancel(r.playerId(), reason);
        }
        queue.clear();
    }

    public void onWorldUnload(World world) {
        for (RtpRequest r : new ArrayList<>(active.values())) {
            if (r.worldName().equals(world.getName()) && r.cancellable()) cancel(r.playerId(), Msg.CANCELLED_WORLD_UNLOADED);
        }
        cache.clear(world.getName());
    }

    /** Idempotent: removes every trace of the request. */
    private void finishRequest(RtpRequest r) {
        active.remove(r.playerId(), r);
        countdowns.remove(r.playerId());
        effects.clear(r.playerId());
        releaseSearch(r);
        releaseHeldChunk(r);
        if (active.isEmpty() && movementRegistered) {
            HandlerList.unregisterAll(movementListener);
            movementRegistered = false;
        }
    }

    // ------------------------------------------------------------------ optional cache

    private void refillTick() {
        if (Bukkit.getOnlinePlayers().isEmpty() || activeSearches > 0 || !queue.isEmpty()) return;
        RtpConfig cfg = cm.config();
        for (Destination d : Destination.values()) {
            resolver.resolve(d, cfg).ifPresent(res -> filler.fill(res.world(), res.settings(), cfg, finder));
        }
    }
}