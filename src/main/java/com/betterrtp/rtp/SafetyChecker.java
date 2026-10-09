package com.betterrtp.rtp;

import com.betterrtp.config.RtpConfig;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.Set;

/** Centralised block-level safety rules. Hazard data lives in {@link HazardCatalog}. */
public final class SafetyChecker {
    private final RtpConfig.Safety opts;
    private final Set<String> blacklist;
    private final HazardCatalog catalog;

    public SafetyChecker(RtpConfig.Safety opts, Set<String> blacklist, HazardCatalog catalog) {
        this.opts = opts;
        this.blacklist = blacklist;
        this.catalog = catalog;
    }

    public RtpConfig.Safety options() { return opts; }

    public boolean isBlacklisted(Material m) { return blacklist.contains(m.name()); }

    public boolean isLeaves(Material m) { return Tag.LEAVES.isTagged(m); }

    private boolean categoryRejected(HazardCatalog.Category c) {
        return switch (c) {
            case WATER -> opts.rejectWater();
            case LAVA -> opts.rejectLava();
            case FIRE -> opts.rejectFire();
            case OTHER -> opts.rejectHazards();
        };
    }

    /** True if this block is a hazard that is currently rejected by configuration. */
    public boolean isHazard(Block b) {
        HazardCatalog.Category c = catalog.classify(b.getType().name());
        if (c != null && categoryRejected(c)) return true;
        return opts.rejectWater() && b.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    /** A block the player's body may occupy: passable, not blacklisted, not a hazard. */
    public boolean isBodyClear(Block b) {
        return !isBlacklisted(b.getType()) && b.isPassable() && !isHazard(b);
    }

    /** A block the player may stand on: solid, reasonably tall, not blacklisted, not a hazard. */
    public boolean isFloorOk(Block floor) {
        Material m = floor.getType();
        if (!m.isSolid() || isBlacklisted(m) || isHazard(floor)) return false;
        return floor.getBoundingBox().getHeight() >= 0.5;
    }

    /** Looks only at the 8 neighbouring columns (floor level up to head level): a few dozen reads at most. */
    public boolean hazardNearby(Block floor, int clearance) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= clearance; dy++) {
                    if (isHazard(floor.getRelative(dx, dy, dz))) return true;
                }
            }
        }
        return false;
    }
}
