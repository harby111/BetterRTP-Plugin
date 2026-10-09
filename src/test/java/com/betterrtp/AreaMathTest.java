package com.betterrtp;

import com.betterrtp.rtp.AreaMath;
import com.betterrtp.rtp.Bounds;
import com.betterrtp.rtp.RandomCandidateGenerator;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AreaMathTest {
    @Test void squareRadiusAroundOrigin() {
        Bounds b = AreaMath.squareBounds(0, 0, 3000);
        assertEquals(new Bounds(-3000, 3000, -3000, 3000), b);
        assertTrue(b.contains(3000, -3000));
        assertFalse(b.contains(3001, 0));
    }

    @Test void centerOffset() {
        Bounds b = AreaMath.squareBounds(500, -200, 100);
        assertEquals(new Bounds(400, 600, -300, -100), b);
    }

    @Test void borderSmallerThanRadiusWins() {
        // border 1000 wide centred on 0 -> edges at +-500; radius 3000 must be clamped inside, minus footprint margin
        Bounds b = AreaMath.effectiveBounds(0, 0, 3000, 0, 0, 1000);
        assertTrue(b.maxX() < 500 && b.minX() > -500, "inside border: " + b);
        assertTrue(b.maxX() + 0.5 <= 500 - AreaMath.BORDER_MARGIN);
        assertTrue(b.minX() + 0.5 >= -500 + AreaMath.BORDER_MARGIN);
    }

    @Test void radiusSmallerThanBorderWins() {
        Bounds b = AreaMath.effectiveBounds(0, 0, 100, 0, 0, 60_000_000);
        assertEquals(new Bounds(-100, 100, -100, 100), b);
    }

    @Test void disjointAreaIsEmpty() {
        Bounds b = AreaMath.effectiveBounds(10_000, 10_000, 50, 0, 0, 1000);
        assertTrue(b.isEmpty());
    }

    @Test void biomeAttemptsMultiplyAndClamp() {
        assertEquals(150, AreaMath.biomeAttempts(50, 3));
        assertEquals(AreaMath.MAX_BIOME_ATTEMPTS, AreaMath.biomeAttempts(500, 10_000));
        assertEquals(1, AreaMath.biomeAttempts(0, 0));
    }

    @Test void candidatesStayInBounds() {
        Bounds b = AreaMath.squareBounds(-40, 70, 25);
        Random rng = new Random(42);
        boolean sawMinX = false, sawMaxX = false;
        for (int i = 0; i < 20_000; i++) {
            var c = RandomCandidateGenerator.next(b, rng);
            assertTrue(b.contains(c.x(), c.z()));
            sawMinX |= c.x() == b.minX();
            sawMaxX |= c.x() == b.maxX();
            assertEquals(c.x() >> 4, c.chunkX());
        }
        assertTrue(sawMinX && sawMaxX, "edges are reachable");
    }

    @Test void emptyBoundsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RandomCandidateGenerator.next(new Bounds(1, 0, 0, 0), new Random()));
    }
}
