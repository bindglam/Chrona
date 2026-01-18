package org.wrd.chrona.async.entity.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles pathfinding frequency based on distance from players
 * Inspired by Leaf's distance-based optimization
 */
public class PathfindingThrottler {
    private static final int DEFAULT_NEAR_DISTANCE = 32;
    private static final int DEFAULT_MID_DISTANCE = 64;
    private static final int DEFAULT_FAR_DISTANCE = 128;

    private static final int DEFAULT_NEAR_INTERVAL = 1;   // Every tick
    private static final int DEFAULT_MID_INTERVAL = 4;    // Every 4 ticks
    private static final int DEFAULT_FAR_INTERVAL = 10;   // Every 10 ticks
    private static final int DEFAULT_VERY_FAR_INTERVAL = 20; // Every 20 ticks

    private final int nearDistance;
    private final int midDistance;
    private final int farDistance;
    private final int nearInterval;
    private final int midInterval;
    private final int farInterval;
    private final int veryFarInterval;

    private final Map<Integer, Long> lastPathfinding = new ConcurrentHashMap<>();

    public PathfindingThrottler() {
        this(
            DEFAULT_NEAR_DISTANCE,
            DEFAULT_MID_DISTANCE,
            DEFAULT_FAR_DISTANCE,
            DEFAULT_NEAR_INTERVAL,
            DEFAULT_MID_INTERVAL,
            DEFAULT_FAR_INTERVAL,
            DEFAULT_VERY_FAR_INTERVAL
        );
    }

    public PathfindingThrottler(
        int nearDistance,
        int midDistance,
        int farDistance,
        int nearInterval,
        int midInterval,
        int farInterval,
        int veryFarInterval
    ) {
        int sanitizedNear = Math.max(0, nearDistance);
        int sanitizedMid = Math.max(sanitizedNear, midDistance);
        int sanitizedFar = Math.max(sanitizedMid, farDistance);

        this.nearDistance = sanitizedNear;
        this.midDistance = sanitizedMid;
        this.farDistance = sanitizedFar;
        this.nearInterval = Math.max(1, nearInterval);
        this.midInterval = Math.max(1, midInterval);
        this.farInterval = Math.max(1, farInterval);
        this.veryFarInterval = Math.max(1, veryFarInterval);
    }

    public boolean shouldPathfind(Mob mob, long currentTick) {
        int entityId = mob.getId();
        Long lastTick = lastPathfinding.get(entityId);

        if (lastTick == null) {
            lastPathfinding.put(entityId, currentTick);
            return true;
        }

        int interval = getInterval(mob);
        if (currentTick - lastTick >= interval) {
            lastPathfinding.put(entityId, currentTick);
            return true;
        }

        return false;
    }

    private int getInterval(Mob mob) {
        // Always pathfind for important entities
        if (mob.getTarget() != null || mob.isAggressive()) {
            return nearInterval;
        }

        double nearestPlayerDist = getNearestPlayerDistance(mob);

        if (nearestPlayerDist < nearDistance) {
            return nearInterval;
        } else if (nearestPlayerDist < midDistance) {
            return midInterval;
        } else if (nearestPlayerDist < farDistance) {
            return farInterval;
        } else {
            return veryFarInterval;
        }
    }

    private double getNearestPlayerDistance(Mob mob) {
        double nearest = Double.MAX_VALUE;

        for (Player player : mob.level().players()) {
            double dist = mob.distanceToSqr(player);
            if (dist < nearest) {
                nearest = dist;
            }
        }

        return Math.sqrt(nearest);
    }

    public void invalidate(int entityId) {
        lastPathfinding.remove(entityId);
    }

    public void clear() {
        lastPathfinding.clear();
    }
}
