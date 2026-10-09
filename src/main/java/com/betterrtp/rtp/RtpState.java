package com.betterrtp.rtp;

public enum RtpState {
    COUNTDOWN,
    EARLY_SEARCH,
    SEARCH_DONE_WAITING,
    QUEUED,
    SEARCHING,
    TELEPORTING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean cancellable() {
        return this == COUNTDOWN || this == EARLY_SEARCH || this == SEARCH_DONE_WAITING || this == QUEUED || this == SEARCHING;
    }
}