package com.betterrtp.rtp;

import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps destinations to real worlds by Environment; never requires manual grass/nether/end assignment. */
public final class WorldResolver {
    public record Resolved(World world, WorldRtpSettings settings) { }
    public enum Status { OK, NOT_FOUND, DISABLED }
    public record Lookup(Status status, Resolved resolved) { }

    /** Settings for a world: its own entry, or the global defaults when it is the configured default world. */
    public Optional<WorldRtpSettings> settingsFor(String worldName, RtpConfig cfg) {
        WorldRtpSettings s = cfg.worlds().get(worldName);
        if (s != null) return Optional.of(s);
        if (worldName.equals(cfg.defaultWorld())) {
            WorldRtpSettings g = cfg.globalDefaults();
            return Optional.of(new WorldRtpSettings(worldName, true, g.radius(), g.shape(), g.minY(), g.maxY(),
                    g.cooldownSeconds(), null, null));
        }
        return Optional.empty();
    }

    public Optional<Resolved> resolve(Destination d, RtpConfig cfg) {
        List<String> candidates = new ArrayList<>();
        switch (d) {
            case OVERWORLD -> candidates.add(cfg.defaultWorld());
            default -> {
                String base = resolveOverworldName(cfg);
                candidates.add(base + d.suffix());
                candidates.add(d.knownName());
            }
        }
        for (String name : candidates) {
            Optional<Resolved> r = usable(name, d, cfg);
            if (r.isPresent()) return r;
        }
        for (String name : cfg.worlds().keySet()) {
            Optional<Resolved> r = usable(name, d, cfg);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    private String resolveOverworldName(RtpConfig cfg) {
        return resolveOverworld(cfg).map(r -> r.world().getName()).orElse(cfg.defaultWorld());
    }

    private Optional<Resolved> resolveOverworld(RtpConfig cfg) {
        return resolve(Destination.OVERWORLD, cfg);
    }

    private Optional<Resolved> usable(String name, Destination d, RtpConfig cfg) {
        World w = Bukkit.getWorld(name);
        if (w == null || w.getEnvironment() != d.environment()) return Optional.empty();
        return settingsFor(name, cfg).filter(WorldRtpSettings::enabled).map(s -> new Resolved(w, s));
    }

    public Lookup byName(String name, RtpConfig cfg) {
        World w = Bukkit.getWorld(name);
        if (w == null) return new Lookup(Status.NOT_FOUND, null);
        Optional<WorldRtpSettings> s = settingsFor(w.getName(), cfg);
        if (s.isEmpty() || !s.get().enabled()) return new Lookup(Status.DISABLED, null);
        return new Lookup(Status.OK, new Resolved(w, s.get()));
    }
}
