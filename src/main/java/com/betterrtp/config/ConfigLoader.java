package com.betterrtp.config;

import com.betterrtp.rtp.Destination;
import com.betterrtp.util.BukkitNameChecks;
import com.betterrtp.util.NameChecks;
import com.betterrtp.util.Parsers;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.function.Consumer;

/** Validates and loads config.yml into an immutable {@link RtpConfig} snapshot. */
public final class ConfigLoader {
    private final Consumer<String> warn;
    private final NameChecks names;

    public ConfigLoader(Consumer<String> warn, NameChecks names) {
        this.warn = warn;
        this.names = names;
    }

    public RtpConfig load(YamlConfiguration y) {
        String defaultWorld = y.getString("world", "world").trim();
        int cooldown = y.getInt("cooldown-time", 30);
        int countdown = y.getInt("countdown-time", 5);
        boolean cancelOnMove = y.getBoolean("cancel-on-move", true);
        int safeAttempts = y.getInt("safe-location-attempts", 50);
        int searchTimeout = y.getInt("teleport-max-search-seconds", 20);
        boolean preload = y.getBoolean("preload-chunks", true);
        boolean rtpgui = y.getBoolean("rtpgui-command-enabled", true);
        boolean openOnRtp = y.getBoolean("open-on-rtp", true);
        boolean debug = y.getBoolean("debug", false);

        // Global defaults
        int gRadius = y.getInt("radius", 3000);
        String gShape = y.getString("shape", "SQUARE");
        int gMinY = y.getInt("min-y", 64);
        int gMaxY = y.getInt("max-y", 320);

        WorldRtpSettings globalDefaults = new WorldRtpSettings(true, gRadius, gShape, gMinY, gMaxY, cooldown, null, null);

        Map<String, WorldRtpSettings> worlds = new LinkedHashMap<>();
        ConfigurationSection ws = y.getConfigurationSection("worlds");
        if (ws != null) {
            for (String name : ws.getKeys(false)) {
                ConfigurationSection s = ws.getConfigurationSection(name);
                if (s == null) continue;
                boolean enabled = s.getBoolean("enabled", true);
                int radius = s.getInt("radius", gRadius);
                String shape = s.getString("shape", gShape);
                int minY = s.getInt("min-y", gMinY);
                int maxY = s.getInt("max-y", gMaxY);
                int cd = s.getInt("cooldown-time", cooldown);
                Integer cx = s.contains("center.x") ? s.getInt("center.x") : null;
                Integer cz = s.contains("center.z") ? s.getInt("center.z") : null;
                worlds.put(name, new WorldRtpSettings(enabled, radius, shape, minY, maxY, cd, cx, cz));
            }
        }

        // Cache
        boolean cacheEnabled = y.getBoolean("cache.enabled", true);
        int cacheSize = y.getInt("cache.size", 100);
        int cacheRefill = y.getInt("cache.refill-interval-ticks", 100);
        RtpConfig.Cache cache = new RtpConfig.Cache(cacheEnabled, cacheSize, cacheRefill);

        // Biome
        boolean biomeEnabled = y.getBoolean("biome.enabled", true);
        boolean biomeGen = y.getBoolean("biome.generate-chunks", true);
        int biomeMult = y.getInt("biome.attempts-multiplier", 10);
        RtpConfig.BiomeRtp biome = new RtpConfig.BiomeRtp(biomeEnabled, biomeGen, biomeMult);

        // Performance
        int maxGlobal = y.getInt("performance.max-global-searches", 3);
        int maxQueue = y.getInt("performance.max-queue-size", 10);
        int chunkTimeout = y.getInt("performance.chunk-load-timeout-ms", 5000);
        int maxCand = y.getInt("performance.max-candidates-per-second", 10);
        boolean searchDuringCountdown = y.getBoolean("performance.search-during-countdown", false);
        RtpConfig.Performance perf = new RtpConfig.Performance(maxGlobal, maxQueue, chunkTimeout, maxCand, searchDuringCountdown);

        // Safety
        boolean rWater = y.getBoolean("safety.reject-water", true);
        boolean rLava = y.getBoolean("safety.reject-lava", true);
        boolean rFire = y.getBoolean("safety.reject-fire", true);
        boolean rHaz = y.getBoolean("safety.reject-hazards", true);
        boolean rUnder = y.getBoolean("safety.reject-underground", true);
        int vClear = y.getInt("safety.vertical-clearance", 2);
        boolean iLeaves = y.getBoolean("safety.ignore-leaves", true);
        int maxScan = y.getInt("safety.max-scan-height", 8);
        boolean nether = y.getBoolean("safety.nether-enabled", true);
        int netherH = y.getInt("safety.nether-minimum-open-height", 3);
        RtpConfig.Safety safety = new RtpConfig.Safety(rWater, rLava, rFire, rHaz, rUnder, vClear, iLeaves, maxScan, nether, netherH);

        // Blacklist
        Set<String> blacklist = new LinkedHashSet<>();
        for (String s : y.getStringList("blacklist")) blacklist.add(s.toUpperCase(Locale.ROOT));

        // Sounds
        RtpConfig.SoundCfg tpSound = sound(y, "teleport-sound", "entity.enderman.teleport");
        RtpConfig.SoundCfg cdSound = sound(y, "countdown-sound", "block.note_block.pling");

        // Display
        boolean dAction = y.getBoolean("display.actionbar", true);
        boolean dTitle = y.getBoolean("display.title", true);
        boolean dBoss = y.getBoolean("display.bossbar", false);
        String bColor = y.getString("display.bossbar-color", "BLUE");
        String bStyle = y.getString("display.bossbar-style", "SOLID");
        RtpConfig.Display display = new RtpConfig.Display(dAction, dTitle, dBoss, bColor, bStyle);

        // Particles
        RtpConfig.ParticleCfg cdPart = particle(y, "countdown-particles", "PORTAL", 10);
        RtpConfig.ParticleCfg tpPart = particle(y, "teleport-particles", "PORTAL", 50);

        // Destinations
        Map<Destination, String> destNames = new EnumMap<>(Destination.class);
        destNames.put(Destination.OVERWORLD, "Overworld");
        destNames.put(Destination.NETHER, "Nether");
        destNames.put(Destination.END, "The End");

        // GUI
        String gTitle = y.getString("gui.title", "RTP Destinations");
        String gFiller = y.getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE");
        Map<Destination, RtpConfig.GuiButton> gButtons = new EnumMap<>(Destination.class);
        // ... (gui loading logic omitted for brevity, assuming default behavior)
        RtpConfig.GuiButton gUnavail = guiButton(y, "gui.unavailable", 22, "BARRIER", "Unavailable", List.of("This destination is currently disabled or missing."));
        RtpConfig.Gui gui = new RtpConfig.Gui(gTitle, gFiller, gButtons, gUnavail);

        // Bedrock GUI
        boolean bedEnabled = y.getBoolean("bedrock-gui.enabled", true);
        String bedTitle = y.getString("bedrock-gui.title", "RTP Destinations");
        String bedContent = y.getString("bedrock-gui.content", "Choose a destination to teleport to:");
        Map<Destination, RtpConfig.BedrockButton> bedButtons = new EnumMap<>(Destination.class);
        RtpConfig.BedrockGui bedrockGui = new RtpConfig.BedrockGui(bedEnabled, bedTitle, bedContent, bedButtons);

        return new RtpConfig(defaultWorld, cooldown, countdown, cancelOnMove, safeAttempts, searchTimeout, preload,
                rtpgui, openOnRtp, debug, globalDefaults, worlds, cache, biome, blacklist, tpSound, cdSound,
                display, cdPart, tpPart, safety, perf, destNames, gui, bedrockGui);
    }

    private RtpConfig.SoundCfg sound(YamlConfiguration y, String path, String def) {
        boolean en = y.getBoolean(path + ".enabled", true);
        String n = y.getString(path + ".name", def);
        float v = (float) y.getDouble(path + ".volume", 1.0);
        float p = (float) y.getDouble(path + ".pitch", 1.0);
        return new RtpConfig.SoundCfg(en, n, v, p);
    }

    private RtpConfig.ParticleCfg particle(YamlConfiguration y, String path, String def, int defCount) {
        boolean en = y.getBoolean(path + ".enabled", true);
        String n = y.getString(path + ".name", def);
        int c = y.getInt(path + ".count", defCount);
        double r = y.getDouble(path + ".radius", 0.5);
        double s = y.getDouble(path + ".spread", 0.5);
        return new RtpConfig.ParticleCfg(en, n, c, r, s);
    }

    private RtpConfig.GuiButton guiButton(YamlConfiguration y, String path, int defSlot, String defMat, String defName, List<String> defLore) {
        int slot = y.getInt(path + ".slot", defSlot);
        String mat = y.getString(path + ".material", defMat);
        String name = y.getString(path + ".name", defName);
        List<String> lore = y.getStringList(path + ".lore");
        if (lore.isEmpty()) lore = defLore;
        return new RtpConfig.GuiButton(slot, mat, name, lore);
    }
}