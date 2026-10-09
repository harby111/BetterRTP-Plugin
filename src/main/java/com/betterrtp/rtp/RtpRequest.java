package com.betterrtp.rtp;

import java.util.UUID;

/**
 * Lifecycle of one RTP request. Pure state: no Bukkit objects, so late callbacks can never
 * resurrect a request (every transition is validated).
 */
public final class RtpRequest {
    private final long id;
    private final UUID playerId;
    private final String worldName;
    private final String label;
    private final String biomeKey;      // null = no biome filter
    private final UUID startWorld;
    private final double startX, startY, startZ;
    private final int totalSeconds;
    private final int cooldownSeconds;
    private RtpState state = RtpState.COUNTDOWN;
    private int remaining;
    private long queuedAtNanos;

    public RtpRequest(long id, UUID playerId, String worldName, String label, String biomeKey, UUID startWorld,
                      double startX, double startY, double startZ, int countdownSeconds, int cooldownSeconds) {
        this.id = id;
        this.playerId = playerId;
        this.worldName = worldName;
        this.label = label;
        this.biomeKey = biomeKey;
        this.startWorld = startWorld;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.totalSeconds = Math.max(1, countdownSeconds);
        this.remaining = this.totalSeconds;
        this.cooldownSeconds = cooldownSeconds;
    }

    public long id() { return id; }
    public UUID playerId() { return playerId; }
    public String worldName() { return worldName; }
    public String label() { return label; }
    public String biomeKey() { return biomeKey; }
    public UUID startWorld() { return startWorld; }
    public double startX() { return startX; }
    public double startY() { return startY; }
    public double startZ() { return startZ; }
    public int totalSeconds() { return totalSeconds; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public RtpState state() { return state; }
    public int remaining() { return remaining; }
    public boolean cancellable() { return state.cancellable(); }
    public long queuedAtNanos() { return queuedAtNanos; }
    public void markQueued(long nanos) { this.queuedAtNanos = nanos; }

    /** Counts one second down; returns the seconds left (0 = countdown finished). */
    public int decrement() {
        if (state == RtpState.COUNTDOWN && remaining > 0) remaining--;
        return remaining;
    }

    /** @return true if the transition was legal and applied. */
    public boolean moveTo(RtpState next) {
        if (!allowed(state, next)) return false;
        state = next;
        return true;
    }

    /** Hard stop from any non-terminal state (disconnect, death, shutdown). */
    public void abort() { if (!state.terminal()) state = RtpState.CANCELLED; }

    private static boolean allowed(RtpState from, RtpState to) {
        return switch (from) {
            case COUNTDOWN -> to == RtpState.QUEUED || to == RtpState.SEARCHING
                    || to == RtpState.CANCELLED || to == RtpState.FAILED;
            case QUEUED -> to == RtpState.SEARCHING || to == RtpState.CANCELLED || to == RtpState.FAILED;
            case SEARCHING -> to == RtpState.TELEPORTING || to == RtpState.CANCELLED || to == RtpState.FAILED;
            case TELEPORTING -> to == RtpState.COMPLETED || to == RtpState.FAILED;
            case COMPLETED, CANCELLED, FAILED -> false;
        };
    }
}
