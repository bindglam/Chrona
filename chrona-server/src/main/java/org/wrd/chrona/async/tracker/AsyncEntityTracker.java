package org.wrd.chrona.async.tracker;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.parallel.EntitySlice;
import org.wrd.chrona.util.FastCollections;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async entity tracker based on Leaf's AsyncTracker pattern
 * Processes entity visibility and packet generation in parallel
 *
 * Lifecycle:
 * 1. tick() - Begin tracking tick, submit tasks
 * 2. onEntitiesTickEnd() - Entity processing complete
 * 3. onTickEnd() - Flush packets to players
 */
public class AsyncEntityTracker {
    private static final Logger LOGGER = LogManager.getLogger(AsyncEntityTracker.class);
    private static final int BATCH_SIZE = 32;
    private static final long TRACKING_TIMEOUT_MS = 20;

    private final ServerLevel level;
    private final LockFreeExecutor executor;
    private final Map<Integer, ChunkMap.TrackedEntity> trackedEntities = new ConcurrentHashMap<>();

    private volatile TrackerContext currentContext;
    private volatile long currentTick = 0;

    // Statistics
    private final AtomicLong totalEntitiesTracked = new AtomicLong(0);
    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private final AtomicLong totalTrackingTime = new AtomicLong(0);

    public AsyncEntityTracker(ServerLevel level, LockFreeExecutor executor) {
        this.level = level;
        this.executor = executor;
        LOGGER.info("AsyncEntityTracker initialized for {}", level.dimension());
    }

    /**
     * Begin a tracking tick
     * Called at the start of entity processing
     */
    public void tick(long tickId) {
        if (!ChronaConfig.trackingParallel) {
            return;
        }

        currentTick = tickId;
        currentContext = new TrackerContext(tickId);
    }

    /**
     * Process tracking for a batch of entities
     * Called during parallel entity processing
     */
    public CompletableFuture<Void> processEntities(Entity[] entities) {
        if (!ChronaConfig.trackingParallel || currentContext == null) {
            return CompletableFuture.completedFuture(null);
        }

        long startTime = System.nanoTime();

        // Split entities into slices for parallel processing
        EntitySlice[] slices = EntitySlice.chunks(entities, BATCH_SIZE);
        List<CompletableFuture<Void>> futures = FastCollections.newObjectList(slices.length);

        for (EntitySlice slice : slices) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processSlice(slice);
            }, executor);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> {
                long elapsed = System.nanoTime() - startTime;
                totalTrackingTime.addAndGet(elapsed);
                totalEntitiesTracked.addAndGet(entities.length);

                if (ex != null) {
                    LOGGER.warn("Error in entity tracking: {}", ex.getMessage());
                }
            });
    }

    private void processSlice(EntitySlice slice) {
        TrackerContext ctx = currentContext;
        if (ctx == null || !ctx.isOpen()) {
            return;
        }

        Entity[] entities = slice.entities();
        for (int i = slice.start(); i < slice.end(); i++) {
            Entity entity = entities[i];
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            try {
                processEntity(entity, ctx);
            } catch (Exception e) {
                LOGGER.warn("Error tracking entity {}: {}", entity.getId(), e.getMessage());
            }
        }
    }

    private void processEntity(Entity entity, TrackerContext ctx) {
        ChunkMap.TrackedEntity tracked = trackedEntities.get(entity.getId());
        if (tracked == null) {
            return;
        }

        // Get players tracking this entity (seenBy contains ServerPlayerConnection)
        Set<ServerPlayerConnection> seenBy = tracked.seenBy;
        if (seenBy == null || seenBy.isEmpty()) {
            return;
        }

        // For each tracking player, check if updates are needed
        for (ServerPlayerConnection connection : seenBy) {
            if (connection == null) {
                continue;
            }

            ServerPlayer player = connection.getPlayer();
            if (player == null || player.isRemoved()) {
                continue;
            }

            // In a full implementation, this would:
            // 1. Check dirty flags (position, velocity, metadata)
            // 2. Generate appropriate packets
            // 3. Queue them via ctx.queuePacket()
        }
    }

    /**
     * Called when entity tick processing is complete
     */
    public void onEntitiesTickEnd() {
        // Placeholder for additional processing between entity tick and flush
    }

    /**
     * Called at end of tick to flush packets
     * Must be called on main thread
     */
    public void onTickEnd() {
        TrackerContext ctx = currentContext;
        if (ctx == null) {
            return;
        }

        try {
            int packetCount = ctx.flush();
            totalPacketsSent.addAndGet(packetCount);

            if (packetCount > 0 && currentTick % 100 == 0) {
                LOGGER.debug("Flushed {} tracker packets for tick {}", packetCount, currentTick);
            }
        } catch (Exception e) {
            LOGGER.error("Error flushing tracker packets", e);
        } finally {
            currentContext = null;
        }
    }

    /**
     * Register an entity for tracking
     */
    public void addTrackedEntity(Entity entity, ChunkMap.TrackedEntity tracked) {
        if (entity != null && tracked != null) {
            trackedEntities.put(entity.getId(), tracked);
        }
    }

    /**
     * Unregister an entity from tracking
     */
    public void removeTrackedEntity(int entityId) {
        trackedEntities.remove(entityId);
    }

    /**
     * Get tracking statistics
     */
    public TrackerStats getStats() {
        return new TrackerStats(
            totalEntitiesTracked.get(),
            totalPacketsSent.get(),
            totalTrackingTime.get(),
            trackedEntities.size()
        );
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        totalEntitiesTracked.set(0);
        totalPacketsSent.set(0);
        totalTrackingTime.set(0);
    }

    /**
     * Shutdown the tracker
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncEntityTracker for {}", level.dimension());
        TrackerContext ctx = currentContext;
        if (ctx != null) {
            ctx.discard();
        }
        trackedEntities.clear();
    }

    public record TrackerStats(
        long entitiesTracked,
        long packetsSent,
        long totalTimeNanos,
        int currentlyTracked
    ) {
        public double avgTimePerEntityNanos() {
            return entitiesTracked > 0 ? (double) totalTimeNanos / entitiesTracked : 0;
        }
    }
}
