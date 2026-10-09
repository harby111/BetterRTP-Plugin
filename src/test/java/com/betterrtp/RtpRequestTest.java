package com.betterrtp;

import com.betterrtp.rtp.RtpRequest;
import com.betterrtp.rtp.RtpState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RtpRequestTest {
    private RtpRequest req(int countdown) {
        return new RtpRequest(1, UUID.randomUUID(), "world", "Overworld", null, UUID.randomUUID(), 0, 64, 0, countdown, 30);
    }

    @Test void countdownCountsDownToZero() {
        RtpRequest r = req(3);
        assertEquals(3, r.remaining());
        assertEquals(2, r.decrement());
        assertEquals(1, r.decrement());
        assertEquals(0, r.decrement());
        assertEquals(0, r.decrement(), "never goes negative");
    }

    @Test void happyPath() {
        RtpRequest r = req(5);
        assertTrue(r.moveTo(RtpState.SEARCHING));
        assertTrue(r.moveTo(RtpState.TELEPORTING));
        assertTrue(r.moveTo(RtpState.COMPLETED));
        assertTrue(r.state().terminal());
    }

    @Test void cancelledRequestCannotBeResurrectedByLateCallbacks() {
        RtpRequest r = req(5);
        r.moveTo(RtpState.SEARCHING);
        assertTrue(r.moveTo(RtpState.CANCELLED));
        assertFalse(r.moveTo(RtpState.TELEPORTING), "late search result must not teleport");
        assertFalse(r.moveTo(RtpState.COMPLETED));
        assertEquals(RtpState.CANCELLED, r.state());
    }

    @Test void teleportInFlightCannotBeCancelledButCanBeAborted() {
        RtpRequest r = req(5);
        r.moveTo(RtpState.SEARCHING);
        r.moveTo(RtpState.TELEPORTING);
        assertFalse(r.cancellable());
        assertFalse(r.moveTo(RtpState.CANCELLED));
        r.abort();
        assertEquals(RtpState.CANCELLED, r.state());
        assertFalse(r.moveTo(RtpState.COMPLETED), "callback after disconnect is ignored");
    }

    @Test void illegalSkipsAreRejected() {
        RtpRequest r = req(5);
        assertFalse(r.moveTo(RtpState.TELEPORTING));
        assertFalse(r.moveTo(RtpState.COMPLETED));
        assertTrue(r.moveTo(RtpState.QUEUED));
        assertTrue(r.cancellable());
    }

    @Test void decrementOnlyDuringCountdown() {
        RtpRequest r = req(5);
        r.moveTo(RtpState.SEARCHING);
        assertEquals(5, r.decrement());
    }
}
