package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One shared 1-second ticker that exists only while at least one countdown is running. */
public final class CountdownManager {
    private final BetterRTPPlugin plugin;
    private final Consumer<UUID> tick;
    private final Set<UUID> ids = new LinkedHashSet<>();
    private BukkitTask task;

    public CountdownManager(BetterRTPPlugin plugin, Consumer<UUID> tick) {
        this.plugin = plugin;
        this.tick = tick;
    }

    public void add(UUID id) {
        ids.add(id);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 20L, 20L);
    }

    public void remove(UUID id) {
        ids.remove(id);
        if (ids.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    public void clear() {
        ids.clear();
        if (task != null) { task.cancel(); task = null; }
    }

    public boolean idle() { return task == null; }

    private void run() {
        for (UUID id : List.copyOf(ids)) {
            if (ids.contains(id)) tick.accept(id);
        }
    }
}
