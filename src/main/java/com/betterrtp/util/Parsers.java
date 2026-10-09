package com.betterrtp.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Keyed;

import java.util.Locale;
import java.util.Optional;

/** Registry-based parsing of config names. Accepts ENUM_STYLE and namespaced.key styles. */
public final class Parsers {
    private Parsers() { }

    public static Optional<Material> material(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Material m = Material.matchMaterial(raw.trim());
        return m == null || m.isLegacy() ? Optional.empty() : Optional.of(m);
    }

    public static Optional<Sound> sound(String raw) {
        return lookup(Registry.SOUNDS, raw);
    }

    /** Only particles without extra data (DUST, ITEM, ... need data and are rejected). */
    public static Optional<Particle> particle(String raw) {
        return lookup(Registry.PARTICLE_TYPE, raw).filter(p -> p.getDataType() == Void.class);
    }

    private static <T extends Keyed> Optional<T> lookup(Registry<T> registry, String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            if (key != null) {
                T t = registry.get(key);
                if (t != null) return Optional.of(t);
            }
        }
        String norm = s.toUpperCase(Locale.ROOT).replace('.', '_').replace(':', '_');
        for (T t : registry) {
            NamespacedKey k = registry.getKey(t);
            if (k != null && k.getKey().replace('.', '_').toUpperCase(Locale.ROOT).equals(norm)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
