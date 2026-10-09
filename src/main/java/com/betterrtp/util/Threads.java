package com.betterrtp.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class Threads {
    private Threads() { }

    /** Runs on the server main thread: immediately if already there, otherwise on the next tick. */
    public static void main(Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) { task.run(); return; }
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
