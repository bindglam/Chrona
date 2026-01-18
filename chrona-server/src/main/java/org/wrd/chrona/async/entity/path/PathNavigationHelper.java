package org.wrd.chrona.async.entity.path;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.wrd.chrona.tick.AsyncTickExecutor;
import org.wrd.chrona.tick.WorldTickManager;

import java.lang.reflect.Method;

/**
 * Helper class to access pathfinding optimization systems
 * Thread-safe and handles fallback when systems are not available
 */
public class PathNavigationHelper {

    /**
     * Get cached path if available and not expired
     */
    public static @Nullable Path getCachedPath(Mob mob, BlockPos start, BlockPos target) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) return null;

        Boolean cacheEnabled = server.chronaConfiguration.optimization.async.pathfinding.enableCache.getValue();
        if (Boolean.FALSE.equals(cacheEnabled)) {
            return null;
        }

        try {
            WorldTickManager tickManager = WorldTickManager.getInstance();
            if (tickManager == null) return null;

            AsyncTickExecutor executor = tickManager.getExecutor(serverLevel);
            if (executor == null) return null;

            PathfindingCache cache = executor.getPathfindingCache();
            if (cache == null) return null;

            return cache.getCachedPath(mob.getId(), start, target);
        } catch (Exception e) {
            // Silent fail - return null to trigger normal pathfinding
            return null;
        }
    }

    /**
     * Cache a computed path
     */
    public static void cachePath(Mob mob, BlockPos start, BlockPos target, Path path) {
        if (path == null || !(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;

        Boolean cacheEnabled = server.chronaConfiguration.optimization.async.pathfinding.enableCache.getValue();
        if (Boolean.FALSE.equals(cacheEnabled)) {
            return;
        }

        try {
            WorldTickManager tickManager = WorldTickManager.getInstance();
            if (tickManager == null) return;

            AsyncTickExecutor executor = tickManager.getExecutor(serverLevel);
            if (executor == null) return;

            PathfindingCache cache = executor.getPathfindingCache();
            if (cache == null) return;

            cache.cachePath(mob.getId(), start, target, path);
        } catch (Exception e) {
            // Silent fail - caching is optional optimization
        }
    }

    /**
     * Check if mob should pathfind this tick based on distance throttling
     */
    public static boolean shouldPathfind(Mob mob, long currentTick) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return true; // Default to allowing pathfinding
        }

        MinecraftServer server = serverLevel.getServer();
        if (server == null) return true;

        Boolean throttlingEnabled = server.chronaConfiguration.optimization.async.pathfinding.enableThrottling.getValue();
        if (Boolean.FALSE.equals(throttlingEnabled)) {
            return true;
        }

        try {
            WorldTickManager tickManager = WorldTickManager.getInstance();
            if (tickManager == null) return true;

            AsyncTickExecutor executor = tickManager.getExecutor(serverLevel);
            if (executor == null) return true;

            PathfindingThrottler throttler = executor.getPathfindingThrottler();
            if (throttler == null) return true;

            return throttler.shouldPathfind(mob, currentTick);
        } catch (Exception e) {
            // Silent fail - allow pathfinding on error
            return true;
        }
    }

    /**
     * Invalidate cached paths for an entity (called when entity is removed or teleported)
     */
    public static void invalidate(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        try {
            WorldTickManager tickManager = WorldTickManager.getInstance();
            if (tickManager == null) return;

            AsyncTickExecutor executor = tickManager.getExecutor(serverLevel);
            if (executor == null) return;

            PathfindingCache cache = executor.getPathfindingCache();
            if (cache != null) {
                cache.invalidate(mob.getId());
            }

            PathfindingThrottler throttler = executor.getPathfindingThrottler();
            if (throttler != null) {
                throttler.invalidate(mob.getId());
            }
        } catch (Exception e) {
            // Silent fail
        }
    }
}
