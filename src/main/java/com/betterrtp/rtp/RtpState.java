package com.betterrtp.rtp;

public enum RtpState {
    COUNTDOWN, QUEUED, SEARCHING, TELEPORTING, COMPLETED, CANCELLED, FAILED;

    public boolean terminal() { return this == COMPLETED || this == CANCELLED || this == FAILED; }
    /** States in which the player may still cancel (not once the teleport itself is in flight). */
    public boolean cancellable() { return this == COUNTDOWN || this == QUEUED || this == SEARCHING; }
}
