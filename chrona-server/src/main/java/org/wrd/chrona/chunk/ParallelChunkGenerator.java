package org.wrd.chrona.chunk;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.chunk.parallel.ChunkIntentPipeline;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel chunk generation system
 * Phase 1: Snapshot (single thread)
 * Phase 2: Parallel Compute (multi thread, no world writes)
 * Phase 3: Intent Assembly (single thread)
 * Phase 4: Commit (single thread, deterministic)
 * Phase 5: Plugin Populate (single thread, compatible)
 */
public class ParallelChunkGenerator {
    private static final Logger LOGGER = LogManager.getLogger(ParallelChunkGenerator.class);
    private static final long GENERATION_TIMEOUT_MS = 5000;

    private final ServerLevel level;
    private final ExecutorService computePool;
    private final int parallelThreads;

    public ParallelChunkGenerator(ServerLevel level, int parallelThreads) {
        this.level = level;
        this.parallelThreads = parallelThreads;
        this.computePool = Executors.newFixedThreadPool(parallelThreads,
            r -> {
                Thread t = new Thread(r, "Chrona-ChunkGen-Worker");
                t.setDaemon(true);
                return t;
            });

        LOGGER.info("ParallelChunkGenerator initialized with {} threads for {}",
            parallelThreads, level.dimension());
    }

    /**
     * Generate multiple chunks in parallel
     *
     * Note: Paper already handles chunk generation asynchronously via ChunkTaskScheduler.
     * This method optimizes by batching requests and maintaining deterministic order.
     */
    public Map<Long, LevelChunk> generateBatch(List<ChunkPos> positions, int radius) {
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }
        long startTime = System.nanoTime();

        try {
            Map<Long, LevelChunk> chunks = generateBatchAsync(positions, radius)
                .get(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            long endTime = System.nanoTime();
            double totalMs = (endTime - startTime) / 1_000_000.0;
            if (!chunks.isEmpty()) {
                LOGGER.debug("Generated {} chunks in {}ms ({}ms/chunk)",
                    chunks.size(),
                    String.format("%.2f", totalMs),
                    String.format("%.2f", totalMs / chunks.size()));
            }

            return chunks;
        } catch (TimeoutException e) {
            LOGGER.warn("Chunk generation timed out for {} chunks", positions.size());
        } catch (Exception e) {
            LOGGER.error("Chunk generation failed", e);
        }

        return Collections.emptyMap();
    }

    public CompletableFuture<Map<Long, LevelChunk>> generateBatchAsync(List<ChunkPos> positions, int radius) {
        if (positions.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        List<CompletableFuture<Map.Entry<Long, LevelChunk>>> futures = new ArrayList<>(positions.size());
        for (ChunkPos pos : positions) {
            CompletableFuture<ChunkResult<ChunkAccess>> future =
                level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);

            futures.add(future.thenApply(result -> {
                ChunkAccess access = result.orElse(null);
                if (access instanceof LevelChunk levelChunk) {
                    return Map.entry(pos.toLong(), levelChunk);
                }
                return null;
            }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                Map<Long, LevelChunk> chunks = new HashMap<>();
                for (CompletableFuture<Map.Entry<Long, LevelChunk>> future : futures) {
                    Map.Entry<Long, LevelChunk> entry = future.getNow(null);
                    if (entry != null) {
                        chunks.put(entry.getKey(), entry.getValue());
                    }
                }
                return chunks;
            });
    }

    /**
     * Process chunk generation through the intent pipeline
     * @param positions Chunk positions to generate
     * @param merger Intent merger for chunk intents
     * @param tickId Current tick ID
     * @param executor Executor for parallel processing
     * @return Future that completes when generation intents are submitted
     */
    public CompletableFuture<ChunkIntentPipeline.PipelineResult> generateWithIntents(
            List<ChunkPos> positions,
            IntentMerger merger,
            long tickId,
            LockFreeExecutor executor) {

        if (positions.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ChunkIntentPipeline.PipelineResult(0, 0, 0));
        }

        WorldGenContext context = captureContext(0);
        return ChunkIntentPipeline.process(positions, context, merger, tickId, executor);
    }

    /**
     * Phase 1: Capture world generation context
     */
    public WorldGenContext captureContext(int radius) {
        long seed = level.getSeed();
        String dimension = level.dimension().toString();

        // Capture neighbor summaries for nearby loaded chunks
        List<WorldGenContext.NeighborSummary> summaries = new ArrayList<>();
        // In a full implementation, would iterate over loaded chunks within radius
        // and create NeighborSummary for each

        return new WorldGenContext(seed, dimension, summaries.toArray(new WorldGenContext.NeighborSummary[0]));
    }

    /**
     * Phase 2: Parallel chunk computation (NO WORLD WRITES)
     */
    private Map<Long, ChunkGenResult> computeParallel(List<ChunkPos> positions, WorldGenContext context) {
        Map<Long, CompletableFuture<ChunkGenResult>> futures = new ConcurrentHashMap<>();

        for (ChunkPos pos : positions) {
            CompletableFuture<ChunkGenResult> future = CompletableFuture.supplyAsync(
                () -> computeChunk(pos, context),
                computePool
            );
            futures.put(pos.toLong(), future);
        }

        // Wait for all computations
        Map<Long, ChunkGenResult> results = new HashMap<>();
        for (Map.Entry<Long, CompletableFuture<ChunkGenResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get(5, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                LOGGER.warn("Chunk generation timed out for {}", ChunkPos.fromLong(entry.getKey()));
            } catch (Exception e) {
                LOGGER.error("Error generating chunk {}", ChunkPos.fromLong(entry.getKey()), e);
            }
        }

        return results;
    }

    /**
     * Pure function: compute chunk data without world access
     */
    private ChunkGenResult computeChunk(ChunkPos pos, WorldGenContext context) {
        long startTime = System.nanoTime();

        // Use vanilla chunk generation (for now)
        // In the future, this would be custom noise generation
        // For now, return empty result and let vanilla handle it
        ChunkGenResult.StructureIntent[] intents = new ChunkGenResult.StructureIntent[0];

        long endTime = System.nanoTime();

        return new ChunkGenResult(
            pos.x, pos.z,
            null, // sections (vanilla handles)
            Collections.emptyMap(), // heightmaps
            intents,
            endTime - startTime
        );
    }

    /**
     * Phase 3: Assemble intents and resolve conflicts
     */
    private void assembleIntents(Map<Long, ChunkGenResult> results) {
        // Sort structure intents by priority
        List<ChunkGenResult.StructureIntent> allIntents = new ArrayList<>();
        for (ChunkGenResult result : results.values()) {
            allIntents.addAll(Arrays.asList(result.getStructureIntents()));
        }

        allIntents.sort(Comparator.comparingLong(ChunkGenResult.StructureIntent::getPriority).reversed());

        // Resolve conflicts (deterministic)
        // For now, no conflicts (vanilla structures)
    }

    /**
     * Phase 4: Commit chunks to world (deterministic order)
     *
     * Note: Paper's chunk system already uses async generation internally.
     * Our parallel chunk generator delegates to Paper's optimized chunk system
     * which handles threading, caching, and neighbor loading efficiently.
     *
     * Canvas and Leaf improvements are mostly in Paper's base implementation,
     * not in additional parallelization layer.
     */
    private Map<Long, LevelChunk> commitChunks(Map<Long, ChunkGenResult> results) {
        Map<Long, LevelChunk> chunks = new HashMap<>();

        // Sort by position for deterministic commit order
        List<Long> sortedKeys = new ArrayList<>(results.keySet());
        sortedKeys.sort(Long::compare);

        // Paper's chunk system already handles:
        // - Async generation (ChunkTaskScheduler)
        // - Neighbor loading and caching
        // - Light calculation optimization
        // - Structure generation parallelization
        //
        // Our role is to batch requests efficiently
        for (Long key : sortedKeys) {
            ChunkPos pos = ChunkPos.fromLong(key);

            // Use Paper's async chunk loading
            // This internally uses thread pool and caching
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            if (chunk instanceof LevelChunk levelChunk) {
                chunks.put(key, levelChunk);
            }
        }

        return chunks;
    }

    public void shutdown() {
        LOGGER.info("Shutting down ParallelChunkGenerator for {}", level.dimension());
        computePool.shutdown();
        try {
            if (!computePool.awaitTermination(5, TimeUnit.SECONDS)) {
                computePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            computePool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simple chunk position holder
     */
    public static class ChunkPos {
        public final int x;
        public final int z;

        public ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public long toLong() {
            return net.minecraft.world.level.ChunkPos.asLong(x, z);
        }

        public static ChunkPos fromLong(long packed) {
            return new ChunkPos(
                net.minecraft.world.level.ChunkPos.getX(packed),
                net.minecraft.world.level.ChunkPos.getZ(packed)
            );
        }
    }
}
