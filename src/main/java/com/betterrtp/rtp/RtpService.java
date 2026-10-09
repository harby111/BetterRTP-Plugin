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
import java.util.UUID;
import java.util.logging.Level;

public final class RtpService {
    public static final String PERM_USE = "betterrtp.use";
    public static final String PERM_BYPASS = "betterrtp.bypass.cooldown";

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

    public RtpRequest activeFor(UUID id) { return active.get(id); }
    public WorldResolver resolver() { return resolver; }
    public RtpConfig config() { return cm.config(); }
    public Messages messages() { return cm.messages(); }
    public CooldownManager cooldowns() { return cooldowns; }

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
        
        if (cfg.performance().searchDuringCountdown()) {
            startEarlySearch(r);
        }
    }

    private void startEarlySearch(RtpRequest r) {
        if (!r.moveTo(RtpState.EARLY_SEARCH)) return;
        Player p = Bukkit.getPlayer(r.playerId());
        if (p == null) { r.abort(); finishRequest(r); return; }
        
        World world = Bukkit.getWorld(r.worldName());
        var settings = resolver.settingsFor(r.worldName(), cm.config()).filter(WorldRtpSettings::enabled);
        if (world == null || settings.isEmpty()) {
            r.moveTo(RtpState.COUNTDOWN, true);
            return;
        }
        
        messages().send(p, Msg.SEARCHING_EARLY);
        NamespacedKey biome = r.biomeKey() == null ? null : NamespacedKey.fromString(r.biomeKey());
        RtpSearchSession s = new RtpSearchSession(plugin, this, r, settings.get(), cm.config(), finder, cache, biome, true);
        sessions.put(r.playerId(), s);
        activeSearches++;
        s.start();
    }

    private void tickCountdown(UUID id) {
        RtpRequest r = active.get(id);
        if (r == null) { countdowns.remove(id); return; }
        
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline() || p.isDead()) { r.abort(); finishRequest(r); return; }
        
        if (r.decrement() > 0) { 
            effects.showCountdown(p, r); 
            return; 
        }
        
        countdowns.remove(id);
        effects.clear(id);
        effects.clearActionbar(p);
        
        if (r.earlySpot() != null) {
            onSearchSuccess(r, r.earlyWorld(), r.earlySpot());
        } else if (r.state() == RtpState.EARLY_SEARCH) {
            r.moveTo(RtpState.SEARCHING);
            r.enableFastMode();
            messages().send(p, Msg.SEARCHING_ACCELERATED);
        } else if (r.hasEarlySearchFailed()) {
            onCountdownDone(p, r);
        } else {
            onCountdownDone(p, r);
        }
    }

    private void onCountdownDone(Player p, RtpRequest r) {
        RtpConfig cfg = cm.config();
        World world = Bukkit.getWorld(r.worldName());
        var settings = resolver.settingsFor(r.worldName(), cfg).filter(WorldRtpSettings::enabled);
        if (world == null) { fail(r, Msg.WORLD_UNAVAILABLE); return; }
        if (settings.isEmpty()) { fail(r, Msg.WORLD_DISABLED); return; }
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

    private void startSearch(RtpRequest r, World world, WorldRtpSettings settings) {
        if (!r.moveTo(RtpState.SEARCHING)) return;
        Player p = Bukkit.getPlayer(r.playerId());
        if (p == null) { r.abort(); finishRequest(r); return; }
        messages().send(p, Msg.SEARCHING);
        NamespacedKey biome = r.biomeKey() == null ? null : NamespacedKey.fromString(r.biomeKey());
        RtpSearchSession s = new RtpSearchSession(plugin, this, r, settings, cm.config(), finder, cache, biome, false);
        sessions.put(r.playerId(), s);
        activeSearches++;
        s.start();
    }

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

    int candidateDelayTicks() {
        int perSecond = cm.config().performance().maxCandidatesPerSecond();
        double ticks = 20.0 * Math.max(1, activeSearches) / perSecond;
        return (int) Math.max(1, Math.min(40, Math.round(ticks)));
    }

    int candidateDelayTicksForRequest(RtpRequest r) {
        if (r.state() == RtpState.EARLY_SEARCH && !r.isFastMode()) {
            int base = candidateDelayTicks();
            return Math.min(100, Math.max(5, base * 4));
        }
        return candidateDelayTicks();
    }

    void onSearchFailed(RtpRequest r, Msg reason) {
        if (r.state() == RtpState.EARLY_SEARCH) {
            r.markEarlySearchFailed();
            releaseSearch(r);
            return;
        }
        fail(r, reason);
    }

    void onEarlySearchSuccess(RtpRequest r, World world, Spot spot) {
        if (!r.moveTo(RtpState.SEARCH_DONE_WAITING)) return;
        r.setEarlySpot(world, spot);
        releaseSearch(r);
    }

    void onSearchSuccess(RtpRequest r, World world, Spot spot) {
        Player p = Bukkit.getPlayer(r.playerId());
        if (p == null || !p.isOnline() || p.isDead()) { r.abort(); finishRequest(r); return; }
        if (!r.moveTo(RtpState.TELEPORTING)) { finishRequest(r); return; }
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

    public boolean cancel(UUID id, Msg reason) {
        RtpRequest r = active.get(id);
        if (r == null || !r.moveTo(RtpState.CANCELLED)) return false;
        Player p = Bukkit.getPlayer(id);
        finishRequest(r);
        if (p != null && reason != null) messages().send(p, reason);
        return true;
    }

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

    private void finishRequest(RtpRequest r) {
        active.remove(r.playerId(), r);
        countdowns.remove(r.playerId());
        effects.clear(r.playerId());
        releaseSearch(r);
        if (active.isEmpty() && movementRegistered) {
            HandlerList.unregisterAll(movementListener);
            movementRegistered = false;
        }
    }

    private void refillTick() {
        if (Bukkit.getOnlinePlayers().isEmpty() || activeSearches > 0 || !queue.isEmpty()) return;
        RtpConfig cfg = cm.config();
        for (Destination d : Destination.values()) {
            resolver.resolve(d, cfg).ifPresent(res -> filler.fill(res.world(), res.settings(), cfg, finder));
        }
    }
}