package com.betterrtp.config;

import com.betterrtp.config.RtpConfig.*;
import com.betterrtp.rtp.Destination;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.function.Consumer;

/**
 * Parses and validates a configuration section into an immutable {@link RtpConfig}.
 * Invalid values produce one concise warning and fall back to a safe default; this class never throws for bad values.
 */
public final class ConfigLoader {
    public static final List<String> DEFAULT_BLACKLIST = List.of("BEDROCK", "OAK_LEAVES", "SPRUCE_LEAVES",
            "BIRCH_LEAVES", "JUNGLE_LEAVES", "ACACIA_LEAVES", "DARK_OAK_LEAVES", "MANGROVE_LEAVES");
    private static final int MAX_RADIUS = 29_999_984;
    private static final int[] DEFAULT_SLOTS = {11, 13, 15};

    private final Consumer<String> warn;
    private final NameChecks checks;

    public ConfigLoader(Consumer<String> warn, NameChecks checks) {
        this.warn = warn;
        this.checks = checks;
    }

    public RtpConfig load(ConfigurationSection c) {
        String defaultWorld = str(c, "world", "world");
        if (defaultWorld.isBlank()) { warn.accept("'world' is blank; using 'world'"); defaultWorld = "world"; }

        int cooldown = intMin(c, "cooldown-time", 30, 0);
        int countdown = intRange(c, "countdown-time", 5, 1, 60);
        int radius = intRange(c, "radius", 3000, 1, MAX_RADIUS);
        String shape = shape(c, "shape", "global");
        int[] y = yRange(c, "min-y", "max-y", 64, 320, "global");
        int attempts = intRange(c, "safe-location-attempts", 50, 1, 500);
        int timeout = intRange(c, "teleport-max-search-seconds", 20, 1, 300);

        WorldRtpSettings global = new WorldRtpSettings(defaultWorld, true, radius, shape, y[0], y[1], cooldown, null, null);

        Map<String, WorldRtpSettings> worlds = new LinkedHashMap<>();
        ConfigurationSection ws = c.getConfigurationSection("worlds");
        if (ws != null) {
            for (String name : ws.getKeys(false)) {
                ConfigurationSection w = ws.getConfigurationSection(name);
                if (w == null || name.isBlank()) { warn.accept("worlds." + name + " is not a section; ignored"); continue; }
                worlds.put(name, world(name, w, global));
            }
        }

        ConfigurationSection cs = sec(c, "location-cache");
        Cache cache = new Cache(cs.getBoolean("enabled", false), intRange(cs, "size", 25, 0, 200),
                intRange(cs, "refill-interval-ticks", 200, 20, 72_000));

        ConfigurationSection bs = sec(c, "biome-rtp");
        BiomeRtp biome = new BiomeRtp(bs.getBoolean("enabled", true), bs.getBoolean("generate-chunks", true),
                intRange(bs, "attempts-multiplier", 3, 1, 10));

        Set<String> blacklist = new LinkedHashSet<>();
        List<String> names = c.isList("blacklisted-blocks") ? c.getStringList("blacklisted-blocks") : DEFAULT_BLACKLIST;
        for (String n : names) {
            String u = n.trim().toUpperCase(Locale.ROOT);
            if (checks.material(u)) blacklist.add(u);
            else warn.accept("blacklisted-blocks: unknown material '" + n + "' ignored");
        }

        SoundCfg tpSound = sound(sec(c, "teleport-sound"), "teleport-sound", "ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f);
        SoundCfg cdSound = sound(sec(c, "countdown-sound"), "countdown-sound", "BLOCK_NOTE_BLOCK_BANJO", 0.5f, 1.0f);

        ConfigurationSection ds = sec(c, "countdown-display");
        String color = str(ds, "bossbar-color", "GREEN").toUpperCase(Locale.ROOT);
        if (bossColor(color) == null) { warn.accept("countdown-display.bossbar-color '" + color + "' invalid; using GREEN"); color = "GREEN"; }
        String style = str(ds, "bossbar-style", "SEGMENTED_10").toUpperCase(Locale.ROOT);
        if (bossOverlay(style) == null) { warn.accept("countdown-display.bossbar-style '" + style + "' invalid; using SEGMENTED_10"); style = "SEGMENTED_10"; }
        Display display = new Display(ds.getBoolean("actionbar-enabled", true), ds.getBoolean("title-enabled", false),
                ds.getBoolean("bossbar-enabled", false), color, style);

        ConfigurationSection ps = sec(c, "particles");
        ParticleCfg cdP = particle(sec(ps, "countdown"), "particles.countdown", "PORTAL", 12, 1.1, 0.0);
        ParticleCfg tpP = particle(sec(ps, "teleport"), "particles.teleport", "CLOUD", 28, 0.0, 0.6);

        ConfigurationSection ss = sec(c, "safety");
        ConfigurationSection uc = sec(ss, "underground-check");
        ConfigurationSection nc = sec(ss, "nether");
        Safety safety = new Safety(ss.getBoolean("reject-water", true), ss.getBoolean("reject-lava", true),
                ss.getBoolean("reject-fire", true), ss.getBoolean("reject-hazards", true),
                ss.getBoolean("reject-underground", true), intRange(ss, "vertical-clearance", 2, 2, 4),
                uc.getBoolean("ignore-leaves", true), intRange(uc, "max-scan-height", 64, 8, 320),
                nc.getBoolean("enabled", true), intRange(nc, "minimum-open-height", 8, 3, 32));

        ConfigurationSection pf = sec(c, "performance");
        boolean searchDuringCountdown = pf.getBoolean("search-during-countdown", false);
        Performance perf = new Performance(intRange(pf, "max-global-active-searches", 4, 1, 16),
                intRange(pf, "max-queue-size", 16, 0, 100), intRange(pf, "chunk-load-timeout-ms", 3000, 250, 30_000),
                intRange(pf, "max-candidates-per-second", 10, 1, 100), searchDuringCountdown);

        Map<Destination, String> destNames = new EnumMap<>(Destination.class);
        ConfigurationSection dn = sec(c, "destination-names");
        for (Destination d : Destination.values()) destNames.put(d, str(dn, d.key(), capitalize(d.key())));

        return new RtpConfig(defaultWorld, cooldown, countdown, c.getBoolean("cancel-on-move", true), attempts, timeout,
                c.getBoolean("preload-chunks", true), c.getBoolean("rtpgui-command-enabled", true),
                c.getBoolean("open-on-rtp", true), c.getBoolean("debug", false), global,
                Collections.unmodifiableMap(worlds), cache, biome, Collections.unmodifiableSet(blacklist),
                tpSound, cdSound, display, cdP, tpP, safety, perf, destNames, gui(c), bedrock(c));
    }

    // ---------------------------------------------------------------- worlds

    private WorldRtpSettings world(String name, ConfigurationSection w, WorldRtpSettings g) {
        String ctx = "worlds." + name;
        int r = intRange(w, "radius", g.radius(), 1, MAX_RADIUS, ctx + ".radius");
        String shape = w.contains("shape") ? shape(w, "shape", ctx) : g.shape();
        int[] y = w.contains("min-y") || w.contains("max-y")
                ? yRange(w, "min-y", "max-y", g.minY(), g.maxY(), ctx) : new int[]{g.minY(), g.maxY()};
        int cd = w.contains("cooldown-time") ? intMin(w, "cooldown-time", g.cooldownSeconds(), 0, ctx + ".cooldown-time") : g.cooldownSeconds();
        Integer cx = null, cz = null;
        ConfigurationSection cen = w.getConfigurationSection("center");
        if (cen != null) {
            if (cen.isInt("x") && cen.isInt("z")) { cx = cen.getInt("x"); cz = cen.getInt("z"); }
            else warn.accept(ctx + ".center needs integer x and z; using the WorldBorder centre");
        }
        return new WorldRtpSettings(name, w.getBoolean("enabled", true), r, shape, y[0], y[1], cd, cx, cz);
    }

    // ---------------------------------------------------------------- GUI

    private Gui gui(ConfigurationSection c) {
        ConfigurationSection g = sec(c, "gui");
        Map<Destination, GuiButton> buttons = new EnumMap<>(Destination.class);
        Set<Integer> used = new HashSet<>();
        String[] mats = {"GRASS_BLOCK", "NETHERRACK", "END_STONE"};
        for (Destination d : Destination.values()) {
            ConfigurationSection i = sec(sec(g, "items"), d.key());
            int slot = i.getInt("slot", DEFAULT_SLOTS[d.ordinal()]);
            if (slot < 0 || slot > 26 || !used.add(slot)) {
                warn.accept("gui.items." + d.key() + ".slot '" + slot + "' invalid or duplicated; using " + DEFAULT_SLOTS[d.ordinal()]);
                slot = DEFAULT_SLOTS[d.ordinal()];
                while (used.contains(slot)) slot = (slot + 1) % 27;
                used.add(slot);
            }
            buttons.put(d, new GuiButton(slot, material(i.getString("material"), mats[d.ordinal()], "gui.items." + d.key() + ".material"),
                    str(i, "name", "<white><destination>"), i.getStringList("lore")));
        }
        ConfigurationSection u = sec(g, "unavailable");
        GuiButton unavailable = new GuiButton(-1, material(u.getString("material"), "BARRIER", "gui.unavailable.material"),
                str(u, "name", "<red><destination> <dark_gray>(unavailable)"), u.getStringList("lore"));
        return new Gui(str(g, "title", "<green>BetterRTP"),
                material(sec(g, "filler").getString("material"), "GRAY_STAINED_GLASS_PANE", "gui.filler.material"),
                buttons, unavailable);
    }

    private BedrockGui bedrock(ConfigurationSection c) {
        ConfigurationSection b = sec(c, "bedrock-gui");
        Map<Destination, BedrockButton> buttons = new EnumMap<>(Destination.class);
        for (Destination d : Destination.values()) {
            ConfigurationSection s = sec(sec(b, "buttons"), d.key());
            ConfigurationSection img = sec(s, "image");
            String type = null, data = "";
            if (img.getBoolean("enabled", false)) {
                String t = str(img, "type", "PATH").toUpperCase(Locale.ROOT);
                String dat = str(img, "data", "");
                if ((t.equals("PATH") || t.equals("URL")) && !dat.isBlank()) { type = t; data = dat; }
                else warn.accept("bedrock-gui.buttons." + d.key() + ".image invalid (type PATH/URL and non-empty data); using a plain button");
            }
            buttons.put(d, new BedrockButton(s.getBoolean("enabled", true), str(s, "text", capitalize(d.key())), type, data));
        }
        return new BedrockGui(b.getBoolean("enabled", true), str(b, "title", "BetterRTP"),
                str(b, "content", "Choose where you want to teleport."), buttons);
    }

    // ---------------------------------------------------------------- helpers

    private SoundCfg sound(ConfigurationSection s, String ctx, String defName, float defVol, float defPitch) {
        String name = str(s, "sound-type", defName).toUpperCase(Locale.ROOT);
        if (!checks.sound(name)) { warn.accept(ctx + ".sound-type '" + name + "' unknown; using " + defName); name = defName; }
        float vol = (float) s.getDouble("volume", defVol);
        if (!(vol >= 0f && vol <= 10f)) { warn.accept(ctx + ".volume out of range (0-10); using " + defVol); vol = defVol; }
        float pitch = (float) s.getDouble("pitch", defPitch);
        if (!(pitch >= 0.5f && pitch <= 2f)) { warn.accept(ctx + ".pitch out of range (0.5-2); using " + defPitch); pitch = defPitch; }
        return new SoundCfg(s.getBoolean("enabled", true), name, vol, pitch);
    }

    private ParticleCfg particle(ConfigurationSection s, String ctx, String defName, int defCount, double defRadius, double defSpread) {
        String name = str(s, "type", defName).toUpperCase(Locale.ROOT);
        if (!checks.particle(name)) { warn.accept(ctx + ".type '" + name + "' unknown/unsupported; using " + defName); name = defName; }
        int count = s.getInt("count", defCount);
        if (count < 0 || count > 100) { int fixed = Math.max(0, Math.min(100, count)); warn.accept(ctx + ".count " + count + " out of range (0-100); using " + fixed); count = fixed; }
        double radius = s.getDouble("radius", defRadius);
        if (!(radius >= 0 && radius <= 16)) { warn.accept(ctx + ".radius out of range (0-16); using " + defRadius); radius = defRadius; }
        double spread = s.getDouble("spread", defSpread);
        if (!(spread >= 0 && spread <= 16)) { warn.accept(ctx + ".spread out of range (0-16); using " + defSpread); spread = defSpread; }
        return new ParticleCfg(s.getBoolean("enabled", true), name, count, radius, spread);
    }

    private String material(String value, String def, String ctx) {
        if (value == null) return def;
        String u = value.trim().toUpperCase(Locale.ROOT);
        if (checks.material(u)) return u;
        warn.accept(ctx + " '" + value + "' unknown; using " + def);
        return def;
    }

    private String shape(ConfigurationSection s, String key, String ctx) {
        String v = str(s, key, "SQUARE").toUpperCase(Locale.ROOT);
        if (!v.equals("SQUARE")) { warn.accept(ctx + ": shape '" + v + "' is not supported (only SQUARE); using SQUARE"); return "SQUARE"; }
        return v;
    }

    private int[] yRange(ConfigurationSection s, String minKey, String maxKey, int defMin, int defMax, String ctx) {
        int min = s.getInt(minKey, defMin), max = s.getInt(maxKey, defMax);
        if (min >= max) { warn.accept(ctx + ": " + minKey + " (" + min + ") must be below " + maxKey + " (" + max + "); using " + defMin + ".." + defMax); return new int[]{defMin, defMax}; }
        return new int[]{min, max};
    }

    private int intMin(ConfigurationSection s, String key, int def, int min) { return intMin(s, key, def, min, key); }
    private int intMin(ConfigurationSection s, String key, int def, int min, String ctx) {
        int v = s.getInt(key, def);
        if (v < min) { warn.accept(ctx + " must be >= " + min + " (was " + v + "); using " + def); return def; }
        return v;
    }
    private int intRange(ConfigurationSection s, String key, int def, int min, int max) { return intRange(s, key, def, min, max, key); }
    private int intRange(ConfigurationSection s, String key, int def, int min, int max, String ctx) {
        int v = s.getInt(key, def);
        if (v < min) { warn.accept(ctx + " must be >= " + min + " (was " + v + "); using " + def); return def; }
        if (v > max) { warn.accept(ctx + " must be <= " + max + " (was " + v + "); using " + max); return max; }
        return v;
    }

    private static String str(ConfigurationSection s, String key, String def) {
        String v = s.getString(key);
        return v == null ? def : v;
    }

    private static ConfigurationSection sec(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
    }

    private static String capitalize(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    public static BossBar.Color bossColor(String name) {
        try { return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    public static BossBar.Overlay bossOverlay(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.equals("SOLID")) return BossBar.Overlay.PROGRESS;
        if (n.startsWith("SEGMENTED_")) n = "NOTCHED_" + n.substring("SEGMENTED_".length());
        try { return BossBar.Overlay.valueOf(n); } catch (IllegalArgumentException e) { return null; }
    }
}