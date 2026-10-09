package com.betterrtp.rtp;

import org.bukkit.World;

/** The three fixed RTP destinations, mapped to worlds by {@link World.Environment}. */
public enum Destination {
    OVERWORLD(World.Environment.NORMAL, "overworld", "world", ""),
    NETHER(World.Environment.NETHER, "nether", "world_nether", "_nether"),
    END(World.Environment.THE_END, "end", "world_the_end", "_the_end");

    private final World.Environment environment;
    private final String key;
    private final String knownName;
    private final String suffix;

    Destination(World.Environment environment, String key, String knownName, String suffix) {
        this.environment = environment;
        this.key = key;
        this.knownName = knownName;
        this.suffix = suffix;
    }

    public World.Environment environment() { return environment; }
    /** Config key (overworld / nether / end). */
    public String key() { return key; }
    /** Conventional world name (world_nether / world_the_end). */
    public String knownName() { return knownName; }
    /** Suffix appended to the Overworld name to guess the sibling world. */
    public String suffix() { return suffix; }
}
