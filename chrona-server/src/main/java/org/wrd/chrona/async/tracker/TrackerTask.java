package org.wrd.chrona.async.tracker;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A single tracking task for one entity
 * Computes visibility and generates packets without blocking main thread
 */
public class TrackerTask implements Callable<TrackerTask.Result> {
    private static final Logger LOGGER = LogManager.getLogger(TrackerTask.class);

    private final Entity entity;
    private final ChunkMap.TrackedEntity trackedEntity;
    private final Set<ServerPlayerConnection> seenBy;
    private final TrackerContext context;
    private final int viewDistance;

    public TrackerTask(
            Entity entity,
            ChunkMap.TrackedEntity trackedEntity,
            Set<ServerPlayerConnection> seenBy,
            TrackerContext context,
            int viewDistance) {
        this.entity = entity;
        this.trackedEntity = trackedEntity;
        this.seenBy = seenBy;
        this.context = context;
        this.viewDistance = viewDistance;
    }

    @Override
    public Result call() {
        try {
            int packetsQueued = 0;
            int playersUpdated = 0;

            // Get server entity for packet generation
            ServerEntity serverEntity = trackedEntity.serverEntity;
            if (serverEntity == null) {
                return new Result(entity.getId(), 0, 0, false);
            }

            // Update tracking for each connection that can see this entity
            for (ServerPlayerConnection connection : seenBy) {
                if (connection == null) {
                    continue;
                }

                ServerPlayer player = connection.getPlayer();
                if (player == null || player.isRemoved()) {
                    continue;
                }

                // Check if player is in range
                if (isInRange(player)) {
                    // Generate and queue update packets
                    // In a full implementation, this would check dirty flags
                    // and generate appropriate packets (position, velocity, metadata, etc.)
                    playersUpdated++;
                }
            }

            return new Result(entity.getId(), packetsQueued, playersUpdated, true);

        } catch (Exception e) {
            LOGGER.warn("Error in tracker task for entity {}: {}", entity.getId(), e.getMessage());
            return new Result(entity.getId(), 0, 0, false);
        }
    }

    private boolean isInRange(ServerPlayer player) {
        if (entity.isRemoved() || player.isRemoved()) {
            return false;
        }

        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();
        double distSq = dx * dx + dz * dz;

        // View distance in blocks (chunks * 16)
        double maxDist = viewDistance * 16.0;
        return distSq <= maxDist * maxDist;
    }

    public Entity getEntity() {
        return entity;
    }

    /**
     * Result of a tracking task
     */
    public record Result(
            int entityId,
            int packetsQueued,
            int playersUpdated,
            boolean success
    ) {}
}
