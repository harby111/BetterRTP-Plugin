package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.WorldRtpSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SafeLocationFinder {

    private final BetterRTPPlugin plugin;
    private final Random random = new Random();

    public SafeLocationFinder(BetterRTPPlugin plugin) {
        this.plugin = plugin;
    }

    public record Bounds(int minX, int maxX, int minZ, int maxZ) {}

    public CompletableFuture<Location> findSafeLocation(World world) {
        CompletableFuture<Location> future = new CompletableFuture<>();

        WorldRtpSettings settings = plugin.getConfigManager().getWorldSettings().get(world.getName());

        int minX = settings != null ? settings.getMinX() : -3000;
        int maxX = settings != null ? settings.getMaxX() : 3000;
        int minZ = settings != null ? settings.getMinZ() : -3000;
        int maxZ = settings != null ? settings.getMaxZ() : 3000;

        searchAsync(world, minX, maxX, minZ, maxZ, 0, plugin.getConfigManager().getMaxAttempts(), future);

        return future;
    }

    public Bounds getBounds(World world) {
        WorldRtpSettings settings = plugin.getConfigManager().getWorldSettings().get(world.getName());

        int minX = settings != null ? settings.getMinX() : -3000;
        int maxX = settings != null ? settings.getMaxX() : 3000;
        int minZ = settings != null ? settings.getMinZ() : -3000;
        int maxZ = settings != null ? settings.getMaxZ() : 3000;

        return new Bounds(minX, maxX, minZ, maxZ);
    }

    public Location attemptSafeLocation(World world, Bounds bounds) {
        int minX = Math.min(bounds.minX(), bounds.maxX());
        int maxX = Math.max(bounds.minX(), bounds.maxX());
        int minZ = Math.min(bounds.minZ(), bounds.maxZ());
        int maxZ = Math.max(bounds.minZ(), bounds.maxZ());

        int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
        int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));

        int y = world.getHighestBlockYAt(x, z);
        Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);

        return isSafe(loc) ? loc : null;
    }

    private void searchAsync(
            World world,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int attempt,
            int maxAttempts,
            CompletableFuture<Location> future
    ) {
        if (attempt >= maxAttempts) {
            future.complete(null);
            return;
        }

        int x = random.nextInt(Math.max(1, maxX - minX + 1)) + minX;
        int z = random.nextInt(Math.max(1, maxZ - minZ + 1)) + minZ;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);

            if (isSafe(loc)) {
                future.complete(loc);
            } else {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                        searchAsync(world, minX, maxX, minZ, maxZ, attempt + 1, maxAttempts, future));
            }
        });
    }

    private boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        Set<Material> blacklist = plugin.getConfigManager().getBlacklistedBlocks();

        if (blacklist.contains(ground.getType()) || blacklist.contains(feet.getType())) {
            return false;
        }

        return ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir();
    }
}