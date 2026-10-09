package com.betterrtp.config;

/** Immutable per-world RTP settings. centerX/centerZ are null when the WorldBorder centre should be used. */
public record WorldRtpSettings(String worldName, boolean enabled, int radius, String shape,
                               int minY, int maxY, int cooldownSeconds, Integer centerX, Integer centerZ) {
    public boolean hasCenter() { return centerX != null && centerZ != null; }
}
