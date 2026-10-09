package com.betterrtp.rtp;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Lifecycle of one RTP request. Pure state: no Bukkit objects, so late callbacks can never
 * resurrect a request (every transition is validated).
 */
public final class RtpRequest {
    private final long id;
    private final UUID playerId;
    private final String worldName;
    private final String label;
    private final String biomeKey;
    private final UUID startWorld;
    private final double startX, startY, startZ;
    private final int totalSeconds;
    private final int cooldownSeconds;
    private RtpState state = RtpState.COUNTDOWN;
    private int remaining;
    private long queuedAtNanos;
    
    // Early search fields
    private Spot earlySpot;
    private String earlyWorldName;
    private boolean fastMode;
    private boolean earlySearchFailed;

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

    /** @return remaining seconds after decrement. */
    public int decrement() { return --remaining; }

    public void markQueued(long nanoTime) { this.queuedAtNanos = nanoTime; }
    public long queuedAtNanos() { return queuedAtNanos; }

    public boolean moveTo(RtpState next) {
        if (state.isFinal()) return false;
        if (next == RtpState.CANCELLED || next == RtpState.FAILED) { state = next; return true; }
        if (next == RtpState.EARLY_SEARCH && state != RtpState.COUNTDOWN) return false;
        if (next == RtpState.SEARCH_DONE_WAITING && state != RtpState.EARLY_SEARCH) return false;
        if (next == RtpState.SEARCHING && state != RtpState.EARLY_SEARCH && state != RtpState.QUEUED && state != RtpState.COUNTDOWN) return false;
        if (next == RtpState.QUEUED && state != RtpState.COUNTDOWN) return false;
        if (next == RtpState.TELEPORTING && state != RtpState.SEARCHING && state != RtpState.SEARCH_DONE_WAITING) return false;
        if (next == RtpState.COMPLETED && state != RtpState.TELEPORTING) return false;
        state = next;
        return true;
    }

    public boolean moveTo(RtpState next, boolean force) {
        if (force) { if (state.isFinal()) return false; state = next; return true; }
        return moveTo(next);
    }

    /** Hard stop: any state -> CANCELLED. Returns true if it was not already final. */
    public boolean abort() {
        if (state.isFinal()) return false;
        state = RtpState.CANCELLED;
        return true;
    }

    public boolean cancellable() { return state.cancellable(); }

    // Early search methods
    public void setEarlySpot(World w, Spot s) {
        this.earlyWorldName = w.getName();
        this.earlySpot = s;
    }

    public Spot earlySpot() { return earlySpot; }
    public World earlyWorld() { return Bukkit.getWorld(earlyWorldName); }
    
    public void enableFastMode() { this.fastMode = true; }
    public boolean isFastMode() { return fastMode; }
    
    public void markEarlySearchFailed() { this.earlySearchFailed = true; }
    public boolean hasEarlySearchFailed() { return earlySearchFailed; }
}