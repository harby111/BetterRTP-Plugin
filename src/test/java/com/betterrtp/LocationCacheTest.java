package com.betterrtp;

import com.betterrtp.rtp.LocationCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationCacheTest {
    @Test void packingRoundTripsNegatives() {
        long v = LocationCache.pack(-2999, 1234);
        assertEquals(-2999, LocationCache.unpackX(v));
        assertEquals(1234, LocationCache.unpackZ(v));
        long w = LocationCache.pack(7, -9);
        assertEquals(7, LocationCache.unpackX(w));
        assertEquals(-9, LocationCache.unpackZ(w));
    }

    @Test void boundedPerWorldAndDisabledByDefault() {
        LocationCache c = new LocationCache();
        c.offer("w", 1, 1);
        assertEquals(0, c.size("w"), "capacity 0 = disabled");
        c.configure(2);
        c.offer("w", 1, 1); c.offer("w", 2, 2); c.offer("w", 3, 3); c.offer("n", 4, 4);
        assertEquals(2, c.size("w"));
        assertEquals(1, c.size("n"));
        assertEquals(1, LocationCache.unpackX(c.poll("w").getAsLong()));
        c.clear("w");
        assertTrue(c.poll("w").isEmpty());
    }
}
