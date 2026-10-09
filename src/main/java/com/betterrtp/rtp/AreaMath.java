package com.betterrtp.rtp;

/** Pure area maths (no Bukkit access) so it can be unit tested. */
public final class AreaMath {
    /** Distance kept between the player's block centre and the border edge. */
    public static final double BORDER_MARGIN = 2.0;
    public static final int MAX_BIOME_ATTEMPTS = 1000;

    private AreaMath() { }

    /** SQUARE area: centre +/- radius on both axes. */
    public static Bounds squareBounds(int centerX, int centerZ, int radius) {
        long r = Math.max(1, radius);
        return new Bounds(clamp(centerX - r), clamp(centerX + r), clamp(centerZ - r), clamp(centerZ + r));
    }

    /** Block range whose centres (x + 0.5) stay at least {@code margin} inside the border. */
    public static Bounds borderBounds(double borderCenterX, double borderCenterZ, double borderSize, double margin) {
        double half = borderSize / 2.0;
        return new Bounds(
                (int) Math.ceil(borderCenterX - half + margin - 0.5),
                (int) Math.floor(borderCenterX + half - margin - 0.5),
                (int) Math.ceil(borderCenterZ - half + margin - 0.5),
                (int) Math.floor(borderCenterZ + half - margin - 0.5));
    }

    public static Bounds effectiveBounds(int centerX, int centerZ, int radius,
                                         double borderCenterX, double borderCenterZ, double borderSize) {
        return squareBounds(centerX, centerZ, radius)
                .intersect(borderBounds(borderCenterX, borderCenterZ, borderSize, BORDER_MARGIN));
    }

    /** Maximum biome candidates: base attempts * multiplier, clamped to a sane ceiling. */
    public static int biomeAttempts(int baseAttempts, int multiplier) {
        long v = (long) Math.max(1, baseAttempts) * Math.max(1, multiplier);
        return (int) Math.min(MAX_BIOME_ATTEMPTS, v);
    }

    private static int clamp(long v) {
        return (int) Math.max(-29_999_984L, Math.min(29_999_984L, v));
    }
}
