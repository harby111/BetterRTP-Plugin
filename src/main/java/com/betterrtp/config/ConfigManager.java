package com.betterrtp.config;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.util.BukkitNameChecks;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.logging.Level;

/** Loads config.yml into immutable snapshots. A failed reload keeps the previous snapshot. */
public final class ConfigManager {
    private final BetterRTPPlugin plugin;
    private volatile RtpConfig config;
    private volatile Messages messages;

    public ConfigManager(BetterRTPPlugin plugin) { this.plugin = plugin; }

    /** Initial load; never throws (falls back to built-in defaults). */
    public void load() {
        plugin.saveDefaultConfig();
        if (!reload()) {
            plugin.getLogger().severe("Using built-in defaults because config.yml could not be loaded.");
            YamlConfiguration empty = new YamlConfiguration();
            config = loader().load(empty);
            messages = Messages.from(empty);
        }
    }

    public boolean reload() {
        try {
            plugin.reloadConfig();
            var yaml = plugin.getConfig();
            RtpConfig newConfig = loader().load(yaml);
            Messages newMessages = Messages.from(yaml);
            config = newConfig;
            messages = newMessages;
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
            return false;
        }
    }

    private ConfigLoader loader() {
        return new ConfigLoader(msg -> plugin.getLogger().warning("[config] " + msg), new BukkitNameChecks());
    }

    public RtpConfig config() { return config; }
    public Messages messages() { return messages; }
}