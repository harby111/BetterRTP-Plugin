package com.betterrtp;

import com.betterrtp.rtp.CooldownManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CooldownManagerTest {
    @Test void appliesAndExpires() {
        AtomicLong now = new AtomicLong(1_000_000);
        CooldownManager cm = new CooldownManager(now::get);
        UUID id = UUID.randomUUID();
        assertEquals(0, cm.remainingSeconds(id));
        cm.apply(id, 30);
        assertEquals(30, cm.remainingSeconds(id));
        now.addAndGet(10_500);
        assertEquals(20, cm.remainingSeconds(id));   // rounded up
        now.addAndGet(20_000);
        assertEquals(0, cm.remainingSeconds(id));
        assertEquals(0, cm.size(), "expired entry removed from memory");
    }

    @Test void zeroCooldownStoresNothing() {
        CooldownManager cm = new CooldownManager(() -> 0L);
        cm.apply(UUID.randomUUID(), 0);
        assertEquals(0, cm.size());
    }

    @Test void purgeOnApplyDropsStaleEntries() {
        AtomicLong now = new AtomicLong(0);
        CooldownManager cm = new CooldownManager(now::get);
        cm.apply(UUID.randomUUID(), 5);
        now.addAndGet(6_000);
        cm.apply(UUID.randomUUID(), 5);
        assertEquals(1, cm.size());
    }

    @Test void activeCooldownSurvivesQuit() {
        AtomicLong now = new AtomicLong(0);
        CooldownManager cm = new CooldownManager(now::get);
        UUID id = UUID.randomUUID();
        cm.apply(id, 30);
        cm.onQuit(id);
        assertEquals(30, cm.remainingSeconds(id));
    }

    @Test void formatting() {
        assertEquals("45s", CooldownManager.format(45));
        assertEquals("2m", CooldownManager.format(120));
        assertEquals("1m 5s", CooldownManager.format(65));
    }
}
