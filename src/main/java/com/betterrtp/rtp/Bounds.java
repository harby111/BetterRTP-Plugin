package com.betterrtp.rtp;

/** Inclusive block-coordinate rectangle. */
public record Bounds(int minX, int maxX, int minZ, int maxZ) {
    public boolean isEmpty() { return minX > maxX || minZ > maxZ; }
    public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    public int width() { return maxX - minX + 1; }
    public int depth() { return maxZ - minZ + 1; }

    public Bounds intersect(Bounds o) {
        return new Bounds(Math.max(minX, o.minX), Math.min(maxX, o.maxX),
                Math.max(minZ, o.minZ), Math.min(maxZ, o.maxZ));
    }
}
