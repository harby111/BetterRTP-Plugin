package com.betterrtp.rtp;

import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Evaluates ONE X/Z column. Must be called on the main thread with the column's chunk loaded.
 * Surface worlds use the heightmap; the Nether scans a bounded vertical range.
 */
public final class SafeLocationFinder {
    private final SafetyChecker safety;
    private final RtpConfig.Safety opts;

    public SafeLocationFinder(SafetyChecker safety) {
        this.safety = safety;
        this.opts = safety.options();
    }

    /** Configured square area intersected with the live WorldBorder (minus a footprint margin). */
    public Bounds bounds(World world, WorldRtpSettings s) {
        WorldBorder border = world.getWorldBorder();
        int cx = s.hasCenter() ? s.centerX() : (int) Math.floor(border.getCenter().getX());
        int cz = s.hasCenter() ? s.centerZ() : (int) Math.floor(border.getCenter().getZ());
        return AreaMath.effectiveBounds(cx, cz, s.radius(), border.getCenter().getX(), border.getCenter().getZ(), border.getSize());
    }

    public Optional<Spot> evaluate(World world, int x, int z, WorldRtpSettings s, RandomGenerator rng) {
        int clearance = opts.verticalClearance();
        int minStand = Math.max(s.minY(), world.getMinHeight() + 1);
        int maxStand = Math.min(s.maxY(), world.getMaxHeight() - clearance - 1);
        if (world.getEnvironment() == World.Environment.NETHER) {
            maxStand = Math.min(maxStand, world.getLogicalHeight() - clearance - 1);
            return evaluateNether(world, x, z, minStand, maxStand, rng);
        }
        return evaluateSurface(world, x, z, minStand, maxStand);
    }

    private Optional<Spot> evaluateSurface(World world, int x, int z, int minStand, int maxStand) {
        HeightMap hm = opts.ignoreLeaves() ? HeightMap.MOTION_BLOCKING_NO_LEAVES : HeightMap.MOTION_BLOCKING;
        int floorY = world.getHighestBlockYAt(x, z, hm);
        if (floorY < world.getMinHeight()) return Optional.empty();   // void column (End)
        int stand = floorY + 1;
        if (stand < minStand || stand > maxStand) return Optional.empty();
        Block floor = world.getBlockAt(x, floorY, z);
        if (!standingOk(floor)) return Optional.empty();
        if (opts.rejectUnderground() && hasCeiling(world, floor)) return Optional.empty();
        return Optional.of(new Spot(x, stand, z));
    }

    private Optional<Spot> evaluateNether(World world, int x, int z, int minStand, int maxStand, RandomGenerator rng) {
        if (maxStand < minStand) return Optional.empty();
        // Random start height diversifies results; scan downwards (bounded by the configured Y range).
        int start = minStand + rng.nextInt(maxStand - minStand + 1);
        int openNeeded = opts.netherEnabled() && opts.rejectUnderground() ? opts.netherMinimumOpenHeight() : 0;
        for (int stand = start; stand >= minStand; stand--) {
            Block floor = world.getBlockAt(x, stand - 1, z);
            if (!floor.getType().isSolid() || !floor.getRelative(0, 1, 0).isPassable()) continue;
            if (!standingOk(floor)) continue;
            if (openNeeded > 0 && !netherOpen(world, floor, openNeeded)) continue;
            return Optional.of(new Spot(x, stand, z));
        }
        return Optional.empty();
    }

    /** Floor + feet + head (+clearance) + nearby hazards. */
    private boolean standingOk(Block floor) {
        if (!safety.isFloorOk(floor)) return false;
        for (int i = 1; i <= opts.verticalClearance(); i++) {
            if (!safety.isBodyClear(floor.getRelative(0, i, 0))) return false;
        }
        return !safety.hazardNearby(floor, opts.verticalClearance());
    }

    /** Overworld/End: anything solid (leaves optionally ignored) above the player means a cave/overhang. */
    private boolean hasCeiling(World world, Block floor) {
        int from = floor.getY() + opts.verticalClearance() + 1;
        int to = Math.min(floor.getY() + opts.maxScanHeight(), world.getMaxHeight() - 1);
        for (int y = from; y <= to; y++) {
            Block b = world.getBlockAt(floor.getX(), y, floor.getZ());
            if (b.isPassable()) continue;
            if (opts.ignoreLeaves() && safety.isLeaves(b.getType())) continue;
            return true;
        }
        return false;
    }

    /** Nether: enough free vertical space and not a 1-wide tunnel (>=3 of 4 sides open at feet and head). */
    private boolean netherOpen(World world, Block floor, int openNeeded) {
        int topLimit = Math.min(floor.getY() + openNeeded, world.getLogicalHeight() - 1);
        for (int y = floor.getY() + 1; y <= topLimit; y++) {
            Block b = world.getBlockAt(floor.getX(), y, floor.getZ());
            if (!b.isPassable() || safety.isHazard(b)) return false;
        }
        if (floor.getY() + openNeeded > world.getLogicalHeight() - 1) return false;
        int open = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Block feet = floor.getRelative(d[0], 1, d[1]);
            Block head = floor.getRelative(d[0], 2, d[1]);
            if (feet.isPassable() && head.isPassable()) open++;
        }
        return open >= 3;
    }

    /** Biomes are 3D in modern Minecraft: sample at the player's standing position. */
    public boolean biomeMatches(World world, Spot spot, NamespacedKey wanted) {
        Biome biome = world.getBiome(spot.x(), spot.y(), spot.z());
        NamespacedKey actual = Registry.BIOME.getKey(biome);
        return wanted.equals(actual);
    }
}
