package org.wrd.chrona.tick;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import it.unimi.dsi.fastutil.longs.LongList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.async.entity.path.PathfindingCache;
import org.wrd.chrona.async.entity.path.PathfindingThrottler;
import org.wrd.chrona.config.ChronaConfig;
import org.wrd.chrona.chunk.ChunkManager;
import org.wrd.chrona.configuration.ChronaConfiguration;
import org.wrd.chrona.tick.compat.BukkitCompatLayer;
import org.wrd.chrona.tick.compat.PluginEventDispatcher;
import org.wrd.chrona.tick.parallel.EntityParallelProcessor;
import org.wrd.chrona.tick.parallel.RedstoneParallelProcessor;
import org.wrd.chrona.tick.phase.*;
import org.wrd.chrona.tick.scheduler.AsyncScheduler;
import org.wrd.chrona.tick.vanilla.VanillaTickSplitter;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncTickExecutor {
    private static final Logger LOGGER = LogManager.getLogger(AsyncTickExecutor.class);
    private static final long STATS_LOG_INTERVAL = 6000; // Every 5 minutes at 20 TPS

    private final ServerLevel level;
    private final LockFreeExecutor computeExecutor;
    private final IntentMerger intentMerger;
    private final CommitPhase commitPhase;
    private final PluginPhaseExecutor pluginExecutor;
    private final EntityParallelProcessor entityProcessor;
    private final RedstoneParallelProcessor redstoneProcessor;
    private final PluginEventDispatcher eventDispatcher;
    private final AsyncScheduler scheduler;
    private final ChunkManager chunkManager;
    private final PathfindingCache pathfindingCache;
    private final PathfindingThrottler pathfindingThrottler;
    private final VanillaTickSplitter vanillaTickSplitter;

    private long currentTick = 0;
    private boolean useVanillaSplitter = true;

    // Performance tracking
    private long totalSnapshotTime = 0;
    private long totalComputeTime = 0;
    private long totalMergeTime = 0;
    private long totalCommitTime = 0;
    private long totalPluginTime = 0;
    private int failedTicks = 0;

    public AsyncTickExecutor(ServerLevel level, int parallelThreads, int genThreads, int ioThreads, int lightThreads) {
        this.level = level;
        String worldName = level.dimension().toString().replace(":", "-").replace("ResourceKey[", "").replace("]", "");
        this.computeExecutor = new LockFreeExecutor(parallelThreads, ChronaConfig.computeQueueSize, "Chrona-Compute-" + worldName);
        this.intentMerger = new IntentMerger();
        this.commitPhase = new CommitPhase(level);
        this.pluginExecutor = new PluginPhaseExecutor(level.getServer());
        this.entityProcessor = new EntityParallelProcessor(computeExecutor, intentMerger, parallelThreads);
        this.redstoneProcessor = new RedstoneParallelProcessor(computeExecutor, intentMerger);
        this.eventDispatcher = new PluginEventDispatcher();
        this.scheduler = new AsyncScheduler();
        this.chunkManager = new ChunkManager(level, genThreads, ioThreads, lightThreads);

        ChronaConfiguration config = level.getServer().chronaConfiguration;
        int cacheExpiryMs = 5000;
        int cacheMaxSize = 1024;
        int nearDistance = 32;
        int midDistance = 64;
        int farDistance = 128;
        int nearInterval = 1;
        int midInterval = 4;
        int farInterval = 10;
        int veryFarInterval = 20;
        if (config != null) {
            Integer configCacheExpiryMs = config.optimization.async.pathfinding.cacheExpiryMs.getValue();
            Integer configCacheMaxSize = config.optimization.async.pathfinding.cacheMaxSize.getValue();
            Integer configNearDistance = config.optimization.async.pathfinding.nearDistance.getValue();
            Integer configMidDistance = config.optimization.async.pathfinding.midDistance.getValue();
            Integer configFarDistance = config.optimization.async.pathfinding.farDistance.getValue();
            Integer configNearInterval = config.optimization.async.pathfinding.nearInterval.getValue();
            Integer configMidInterval = config.optimization.async.pathfinding.midInterval.getValue();
            Integer configFarInterval = config.optimization.async.pathfinding.farInterval.getValue();
            Integer configVeryFarInterval = config.optimization.async.pathfinding.veryFarInterval.getValue();

            if (configCacheExpiryMs != null) {
                cacheExpiryMs = configCacheExpiryMs;
            }
            if (configCacheMaxSize != null) {
                cacheMaxSize = configCacheMaxSize;
            }
            if (configNearDistance != null) {
                nearDistance = configNearDistance;
            }
            if (configMidDistance != null) {
                midDistance = configMidDistance;
            }
            if (configFarDistance != null) {
                farDistance = configFarDistance;
            }
            if (configNearInterval != null) {
                nearInterval = configNearInterval;
            }
            if (configMidInterval != null) {
                midInterval = configMidInterval;
            }
            if (configFarInterval != null) {
                farInterval = configFarInterval;
            }
            if (configVeryFarInterval != null) {
                veryFarInterval = configVeryFarInterval;
            }
        }

        this.pathfindingCache = new PathfindingCache(cacheMaxSize, cacheExpiryMs);
        this.pathfindingThrottler = new PathfindingThrottler(
            nearDistance,
            midDistance,
            farDistance,
            nearInterval,
            midInterval,
            farInterval,
            veryFarInterval
        );
        this.vanillaTickSplitter = new VanillaTickSplitter(level);

        LOGGER.info("Initialized AsyncTickExecutor for {} with {} compute threads", level.dimension(), parallelThreads);
    }

    public void executeTick(java.util.function.BooleanSupplier hasTimeLeft) {
        currentTick++;

        // Chrona - Set IntentMerger for this tick
        BukkitCompatLayer.setIntentMerger(intentMerger);
        BukkitCompatLayer.beginTick(currentTick);
        intentMerger.beginTick(currentTick);

        try {
            // ============================================
            // CHRONA 5-PHASE TICK PIPELINE - FULLY ACTIVE
            // ============================================

            // Phase 0: Vanilla pre-tick (environment, chunks, blocks)
            // These operations don't benefit from parallelization
            executeVanillaPrePhases(hasTimeLeft);

            // Phase 1: SNAPSHOT - Capture world state (read-only)
            long snapshotStart = System.nanoTime();
            WorldSnapshot snapshot;
            try {
                snapshot = WorldSnapshot.capture(level, currentTick);
            } catch (Exception e) {
                LOGGER.error("Snapshot phase failed at tick {}", currentTick, e);
                failedTicks++;
                fallbackToVanilla(hasTimeLeft);
                return;
            }
            long snapshotEnd = System.nanoTime();
            totalSnapshotTime += (snapshotEnd - snapshotStart);

            // Phase 2: PARALLEL COMPUTE - Intent-only compute (no world writes)
            Entity[] entities = snapshot.getEntityArray();
            long computeStart = System.nanoTime();
            boolean computeOk = true;

            CompletableFuture<Void> entityCompute = entityProcessor.processEntities(snapshot, entities, currentTick);
            CompletableFuture<Void> redstoneCompute = CompletableFuture.completedFuture(null);
            if (ChronaConfig.redstoneParallel && ChronaConfig.redstoneMaxGraphSize != 0) {
                LongList redstonePositions = snapshot.getRedstonePositions(ChronaConfig.redstoneMaxGraphSize);
                if (!redstonePositions.isEmpty()) {
                    if (snapshot.isRedstoneTruncated() && ChronaConfig.redstoneSkipOnTruncated) {
                        if (currentTick % 100 == 0) {
                            LOGGER.warn("Redstone scan truncated at {} nodes; skipping async redstone this tick", ChronaConfig.redstoneMaxGraphSize);
                        }
                    } else {
                        redstoneCompute = redstoneProcessor.processRedstone(snapshot, redstonePositions, currentTick);
                    }
                }
            }

            try {
                CompletableFuture.allOf(entityCompute, redstoneCompute)
                    .get(ChronaConfig.computeTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                computeOk = false;
                LOGGER.warn("Compute phase timeout at tick {} - dropping late results", currentTick);
            } catch (Exception e) {
                computeOk = false;
                LOGGER.error("Error in tick {} compute phase", currentTick, e);
                failedTicks++;
            }
            long computeEnd = System.nanoTime();
            totalComputeTime += (computeEnd - computeStart);

            // Stop accepting intents once compute is done/timed out
            intentMerger.endTick(currentTick);

            long mergeStart = 0, mergeEnd = 0;
            long commitStart = 0, commitEnd = 0;
            if (computeOk) {
                mergeStart = System.nanoTime();
                List<Intent> mergedIntents = intentMerger.merge();
                mergeEnd = System.nanoTime();
                totalMergeTime += (mergeEnd - mergeStart);

                commitStart = System.nanoTime();
                commitPhase.apply(mergedIntents);
                commitEnd = System.nanoTime();
                totalCommitTime += (commitEnd - commitStart);
            }

            intentMerger.clear();

            // Vanilla post-tick (block entities, raids, etc.)
            executeVanillaPostPhases();

            // Phase 5: PLUGIN PHASE - Maintain "single-thread illusion"
            long pluginStart = System.nanoTime();
            try {
                pluginExecutor.execute(() -> {
                    try {
                        scheduler.executePendingTasks();
                        eventDispatcher.dispatchPendingEvents();
                    } catch (Exception e) {
                        LOGGER.error("Plugin phase task failed", e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Plugin phase failed at tick {}", currentTick, e);
                failedTicks++;
            }
            long pluginEnd = System.nanoTime();
            totalPluginTime += (pluginEnd - pluginStart);

            // Periodic logging
            if (currentTick % 100 == 0) {
                logPhaseTimings(snapshotEnd - snapshotStart, computeEnd - computeStart,
                    mergeEnd - mergeStart, commitEnd - commitStart, pluginEnd - pluginStart);
            }

            // Detailed stats every 5 minutes
            if (currentTick % STATS_LOG_INTERVAL == 0) {
                logDetailedStats();
            }
        } finally {
            intentMerger.endTick(currentTick);
            intentMerger.clear();
            BukkitCompatLayer.clearIntentMerger();
            BukkitCompatLayer.endTick(currentTick);
        }
    }

    /**
     * Execute vanilla operations that don't benefit from parallelization
     * (environment, chunks, blocks, fluids)
     */
    private void executeVanillaPrePhases(java.util.function.BooleanSupplier hasTimeLeft) {
        WorldTickManager.setInVanillaTick(true);
        try {
            if (useVanillaSplitter) {
                // Use VanillaTickSplitter for separated pre-tick
                vanillaTickSplitter.executePreTick(hasTimeLeft);
            } else {
                // Fallback: Full vanilla tick
                boolean wasEnabled = org.wrd.chrona.config.ChronaConfig.asyncEnabled;
                try {
                    org.wrd.chrona.config.ChronaConfig.asyncEnabled = false;
                    level.tick(hasTimeLeft);
                } finally {
                    org.wrd.chrona.config.ChronaConfig.asyncEnabled = wasEnabled;
                }
            }
        } finally {
            WorldTickManager.setInVanillaTick(false);
        }
    }

    /**
     * Extract entities from vanilla tick splitter for parallel processing
     */
    private Entity[] extractEntitiesFromVanilla() {
        if (useVanillaSplitter) {
            return vanillaTickSplitter.extractEntities();
        }
        return new Entity[0];
    }

    /**
     * Execute vanilla post-phases (block entities, raids, cleanup)
     */
    private void executeVanillaPostPhases() {
        if (useVanillaSplitter) {
            WorldTickManager.setInVanillaTick(true);
            try {
                vanillaTickSplitter.executePostTick();
            } finally {
                WorldTickManager.setInVanillaTick(false);
            }
        }
    }

    /**
     * Fallback to vanilla tick on critical failure
     */
    private void fallbackToVanilla(java.util.function.BooleanSupplier hasTimeLeft) {
        LOGGER.error("Falling back to vanilla tick at tick {}", currentTick);
        WorldTickManager.setInVanillaTick(true);
        try {
            boolean wasEnabled = org.wrd.chrona.config.ChronaConfig.asyncEnabled;
            try {
                org.wrd.chrona.config.ChronaConfig.asyncEnabled = false;
                level.tick(hasTimeLeft);
            } finally {
                org.wrd.chrona.config.ChronaConfig.asyncEnabled = wasEnabled;
            }
        } finally {
            WorldTickManager.setInVanillaTick(false);
        }
        intentMerger.endTick(currentTick);
        intentMerger.clear();
    }

    private void logPhaseTimings(long snapshot, long compute, long merge, long commit, long plugin) {
        LOGGER.info("Tick phases - Snapshot: {}ms, Compute: {}ms, Merge: {}ms, Commit: {}ms, Plugin: {}ms",
            String.format("%.2f", snapshot / 1_000_000.0),
            String.format("%.2f", compute / 1_000_000.0),
            String.format("%.2f", merge / 1_000_000.0),
            String.format("%.2f", commit / 1_000_000.0),
            String.format("%.2f", plugin / 1_000_000.0));
    }

    public IntentMerger getIntentMerger() {
        return intentMerger;
    }

    public AsyncScheduler getScheduler() {
        return scheduler;
    }

    public PluginEventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public PathfindingCache getPathfindingCache() {
        return pathfindingCache;
    }

    public PathfindingThrottler getPathfindingThrottler() {
        return pathfindingThrottler;
    }

    public VanillaTickSplitter getVanillaTickSplitter() {
        return vanillaTickSplitter;
    }

    public void setUseVanillaSplitter(boolean use) {
        this.useVanillaSplitter = use;
    }

    public boolean isUsingVanillaSplitter() {
        return useVanillaSplitter;
    }

    private void logDetailedStats() {
        LOGGER.info("=== Chrona Tick Executor Stats for {} ===", level.dimension());
        LOGGER.info("  Current tick: {}", currentTick);
        LOGGER.info("  Failed ticks: {}", failedTicks);

        // Phase averages
        if (currentTick > 0) {
            LOGGER.info("  Average phase timings:");
            LOGGER.info("    Snapshot: {}ms", String.format("%.3f", totalSnapshotTime / (currentTick * 1_000_000.0)));
            LOGGER.info("    Compute:  {}ms", String.format("%.3f", totalComputeTime / (currentTick * 1_000_000.0)));
            LOGGER.info("    Merge:    {}ms", String.format("%.3f", totalMergeTime / (currentTick * 1_000_000.0)));
            LOGGER.info("    Commit:   {}ms", String.format("%.3f", totalCommitTime / (currentTick * 1_000_000.0)));
            LOGGER.info("    Plugin:   {}ms", String.format("%.3f", totalPluginTime / (currentTick * 1_000_000.0)));
        }

        // Executor stats
        LockFreeExecutor.ExecutorStats execStats = computeExecutor.getStats();
        LOGGER.info("  Compute executor:");
        LOGGER.info("    Workers: {}", execStats.workerCount());
        LOGGER.info("    Tasks: submitted={}, completed={}, pending={}",
            execStats.submitted(), execStats.completed(), execStats.pending());
        LOGGER.info("    Queue size: {}", execStats.queueSize());
        LOGGER.info("    Rejection rate: {}%", String.format("%.2f", execStats.rejectionRate() * 100));

        // Intent stats
        IntentMerger.MergerStats mergerStats = intentMerger.getStats();
        LOGGER.info("  Intent merger:");
        LOGGER.info("    Submitted: {}, Merged: {}, Pending: {}",
            mergerStats.submitted(), mergerStats.merged(), mergerStats.pending());
        LOGGER.info("    Conflicts: {} ({}%)", mergerStats.conflicts(),
            String.format("%.2f", mergerStats.conflictRate() * 100));

        LOGGER.info("==========================================");
    }

    /**
     * Get current tick executor statistics
     */
    public TickExecutorStats getStats() {
        return new TickExecutorStats(
            currentTick,
            failedTicks,
            currentTick > 0 ? totalSnapshotTime / currentTick : 0,
            currentTick > 0 ? totalComputeTime / currentTick : 0,
            currentTick > 0 ? totalMergeTime / currentTick : 0,
            currentTick > 0 ? totalCommitTime / currentTick : 0,
            currentTick > 0 ? totalPluginTime / currentTick : 0,
            computeExecutor.getStats(),
            intentMerger.getStats()
        );
    }

    public record TickExecutorStats(
        long currentTick,
        int failedTicks,
        long avgSnapshotTimeNanos,
        long avgComputeTimeNanos,
        long avgMergeTimeNanos,
        long avgCommitTimeNanos,
        long avgPluginTimeNanos,
        LockFreeExecutor.ExecutorStats executorStats,
        IntentMerger.MergerStats mergerStats
    ) {
        public double totalAvgTimeMs() {
            return (avgSnapshotTimeNanos + avgComputeTimeNanos + avgMergeTimeNanos +
                    avgCommitTimeNanos + avgPluginTimeNanos) / 1_000_000.0;
        }
    }

    public void shutdown() {
        LOGGER.info("Shutting down AsyncTickExecutor for {}", level.dimension());

        // Log final stats
        logDetailedStats();

        computeExecutor.shutdown();
        chunkManager.shutdown();
        pathfindingCache.clear();
        pathfindingThrottler.clear();

        LOGGER.info("AsyncTickExecutor shutdown complete");
    }
}
