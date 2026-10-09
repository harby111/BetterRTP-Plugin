package com.betterrtp.rtp;

import java.util.random.RandomGenerator;

/** Samples ONE random X/Z at a time. Never builds lists of locations. */
public final class RandomCandidateGenerator {
    public record Candidate(int x, int z) {
        public int chunkX() { return x >> 4; }
        public int chunkZ() { return z >> 4; }
    }

    private RandomCandidateGenerator() { }

    public static Candidate next(Bounds bounds, RandomGenerator rng) {
        if (bounds.isEmpty()) throw new IllegalArgumentException("empty bounds");
        int x = bounds.minX() + rng.nextInt(bounds.width());
        int z = bounds.minZ() + rng.nextInt(bounds.depth());
        return new Candidate(x, z);
    }
}
