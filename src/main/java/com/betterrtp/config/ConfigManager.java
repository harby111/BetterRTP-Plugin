package com.betterrtp.config;

import com.betterrtp.BetterRTPPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ConfigManager {

    private final BetterRTPPlugin plugin;

    private int cooldownSeconds;
    private boolean cancelOnMove;
    private int teleportDelaySeconds;
    private int maxAttempts;

    private boolean searchDuringCountdown;
    private int searchIntervalTicks;
    private int attemptsPerInterval;
    private boolean boostSearchAfterCountdown;
    private int boostIntervalTicks;
    private int boostAttemptsPerInterval;

    private final Set<Material> blacklistedBlocks = new HashSet<>();
    private final Map<String, WorldRtpSettings> worldSettings = new HashMap<>();

    public ConfigManager(BetterRTPPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.cooldownSeconds = config.getInt("settings.cooldown-seconds", 30);
        this.cancelOnMove = config.getBoolean("settings.cancel-on-move", true);
        this.teleportDelaySeconds = config.getInt("settings.teleport-delay-seconds", 3);
        this.maxAttempts = config.getInt("settings.max-attempts", 150);

        this.searchDuringCountdown = config.getBoolean("performance.search-during-countdown", true);
        this.searchIntervalTicks = Math.max(1, config.getInt("performance.search-interval-ticks", 8));
        this.attemptsPerInterval = Math.max(1, config.getInt("performance.attempts-per-interval", 1));
        this.boostSearchAfterCountdown = config.getBoolean("performance.boost-search-after-countdown", true);
        this.boostIntervalTicks = Math.max(1, config.getInt("performance.boost-interval-ticks", 1));
        this.boostAttemptsPerInterval = Math.max(1, config.getInt("performance.boost-attempts-per-interval", 4));

        this.blacklistedBlocks.clear();
        for (String matName : config.getStringList("blacklisted-blocks")) {
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
                blacklistedBlocks.add(mat);
            }
        }

        this.worldSettings.clear();
        if (config.isConfigurationSection("worlds")) {
            for (String worldName : config.getConfigurationSection("worlds").getKeys(false)) {
                boolean enabled = config.getBoolean("worlds." + worldName + ".enabled", true);
                int minX = config.getInt("worlds." + worldName + ".min-x", -3000);
                int maxX = config.getInt("worlds." + worldName + ".max-x", 3000);
                int minZ = config.getInt("worlds." + worldName + ".min-z", -3000);
                int maxZ = config.getInt("worlds." + worldName + ".max-z", 3000);

                worldSettings.put(worldName, new WorldRtpSettings(enabled, minX, maxX, minZ, maxZ));
            }
        }
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isCancelOnMove() {
        return cancelOnMove;
    }

    public int getTeleportDelaySeconds() {
        return teleportDelaySeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isSearchDuringCountdown() {
        return searchDuringCountdown;
    }

    public int getSearchIntervalTicks() {
        return searchIntervalTicks;
    }

    public int getAttemptsPerInterval() {
        return attemptsPerInterval;
    }

    public boolean isBoostSearchAfterCountdown() {
        return boostSearchAfterCountdown;
    }

    public int getBoostIntervalTicks() {
        return boostIntervalTicks;
    }

    public int getBoostAttemptsPerInterval() {
        return boostAttemptsPerInterval;
    }

    public Set<Material> getBlacklistedBlocks() {
        return blacklistedBlocks;
    }

    public Map<String, WorldRtpSettings> getWorldSettings() {
        return worldSettings;
    }
}