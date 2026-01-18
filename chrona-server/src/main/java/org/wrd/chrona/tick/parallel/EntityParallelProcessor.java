package org.wrd.chrona.tick.parallel;

import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.WorldSnapshot;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class EntityParallelProcessor {
    private static final Logger LOGGER = LogManager.getLogger(EntityParallelProcessor.class);
    private static final AtomicBoolean WARNED_DISABLED = new AtomicBoolean(false);
    private static final AtomicBoolean WARNED_NO_PLANNERS = new AtomicBoolean(false);
    private final LockFreeExecutor executor;
    private final IntentMerger intentMerger;
    private final int threads;

    public EntityParallelProcessor(LockFreeExecutor executor, IntentMerger intentMerger, int threads) {
        this.executor = executor;
        this.intentMerger = intentMerger;
        this.threads = threads;
    }

    public CompletableFuture<Void> processEntities(WorldSnapshot snapshot, Entity[] entities, long tickId) {
        if (entities.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        boolean parallelEnabled = ChronaConfig.aiParallel || ChronaConfig.physicsParallel || ChronaConfig.trackingParallel;
        if (!parallelEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        List<EntityIntentComputer> planners = EntityIntentPlannerRegistry.snapshot();
        if (planners.isEmpty()) {
            if (WARNED_NO_PLANNERS.compareAndSet(false, true)) {
                LOGGER.warn("Entity intent compute has no registered planners; parallel entity compute remains idle.");
            }
            return CompletableFuture.completedFuture(null);
        }

        if (WARNED_DISABLED.compareAndSet(false, true)) {
            LOGGER.info("Entity intent compute enabled with {} planner(s)", planners.size());
        }

        EntityIntentPipeline pipeline = new EntityIntentPipeline(planners);
        int sliceSize = Math.max(1, ChronaConfig.entityBatchSize);
        EntitySlice[] slices = EntitySlice.chunks(entities, sliceSize);
        CompletableFuture<?>[] futures = new CompletableFuture<?>[slices.length];

        for (int i = 0; i < slices.length; i++) {
            EntitySlice slice = slices[i];
            futures[i] = submitSlice(snapshot, slice, pipeline, tickId);
        }

        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> submitSlice(WorldSnapshot snapshot, EntitySlice slice, EntityIntentPipeline pipeline, long tickId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    processSlice(snapshot, slice, pipeline, tickId);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void processSlice(WorldSnapshot snapshot, EntitySlice slice, EntityIntentPipeline pipeline, long tickId) {
        if (!intentMerger.isAccepting() || intentMerger.getActiveTick() != tickId) {
            return;
        }

        Entity[] entities = slice.entities();
        for (int i = slice.start(); i < slice.end(); i++) {
            if (!intentMerger.isAccepting() || intentMerger.getActiveTick() != tickId) {
                return;
            }
            Entity entity = entities[i];
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            pipeline.compute(snapshot, entity, intentMerger, tickId);
        }
    }

}
