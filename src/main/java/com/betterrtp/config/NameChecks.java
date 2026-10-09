package com.betterrtp.config;

/** Validates registry-backed names. Runtime uses the Bukkit registries; tests use a lenient stub. */
public interface NameChecks {
    boolean material(String name);
    boolean sound(String name);
    boolean particle(String name);

    static NameChecks lenient() {
        return new NameChecks() {
            @Override public boolean material(String name) { return !name.startsWith("INVALID"); }
            @Override public boolean sound(String name) { return !name.startsWith("INVALID"); }
            @Override public boolean particle(String name) { return !name.startsWith("INVALID"); }
        };
    }
}
