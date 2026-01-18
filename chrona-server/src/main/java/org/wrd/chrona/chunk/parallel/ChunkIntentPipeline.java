package org.wrd.chrona.chunk.parallel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.chunk.ParallelChunkGenerator;
import org.wrd.chrona.chunk.WorldGenContext;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.util.FastCollections;
import org.wrd.chrona.util.lockfree.LockFreeExecutor;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pipeline for parallel chunk generation using intent computers
 * Manages registration and execution of ChunkIntentComputers
 */
public class ChunkIntentPipeline {
    private static final Logger LOGGER = LogManager.getLogger(ChunkIntentPipeline.class);
    private static final List<ChunkIntentComputer> COMPUTERS = new CopyOnWriteArrayList<>();

    // Statistics
    private static final AtomicLong totalChunksProcessed = new AtomicLong(0);
    private static final AtomicLong totalProcessingTime = new AtomicLong(0);

    static {
        // Register default computers
        register(new NoiseIntentComputer());
        register(new StructureIntentComputer());
    }

    /**
     * Register a chunk intent computer
     */
    public static void register(ChunkIntentComputer computer) {
        if (computer != null) {
            COMPUTERS.add(computer);
            // Sort by priority (descending)
            COMPUTERS.sort(Comparator.comparingInt(ChunkIntentComputer::getPriority).reversed());
            LOGGER.info("Registered ChunkIntentComputer: {} (priority: {})",
                computer.getName(), computer.getPriority());
        }
    }

    /**
     * Get all registered computers
     */
    public static List<ChunkIntentComputer> getComputers() {
        return List.copyOf(COMPUTERS);
    }

    /**
     * Get the number of registered computers
     */
    public static int getComputerCount() {
        return COMPUTERS.size();
    }

    /**
     * Process a batch of chunks through the pipeline
     */
    public static CompletableFuture<PipelineResult> process(
            List<ParallelChunkGenerator.ChunkPos> chunks,
            WorldGenContext context,
            IntentMerger merger,
            long tickId,
            LockFreeExecutor executor) {

        if (chunks.isEmpty() || COMPUTERS.isEmpty()) {
            return CompletableFuture.completedFuture(new PipelineResult(0, 0, 0));
        }

        long startTime = System.nanoTime();
        List<CompletableFuture<Void>> futures = FastCollections.newObjectList(chunks.size());

        for (ParallelChunkGenerator.ChunkPos chunk : chunks) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processChunk(chunk.x, chunk.z, context, merger, tickId);
            }, executor);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                long elapsed = System.nanoTime() - startTime;
                totalChunksProcessed.addAndGet(chunks.size());
                totalProcessingTime.addAndGet(elapsed);

                return new PipelineResult(
                    chunks.size(),
                    COMPUTERS.size(),
                    elapsed
                );
            });
    }

    /**
     * Process a single chunk through all computers
     */
    private static void processChunk(
            int chunkX,
            int chunkZ,
            WorldGenContext context,
            IntentMerger merger,
            long tickId) {

        for (ChunkIntentComputer computer : COMPUTERS) {
            try {
                if (computer.supports(chunkX, chunkZ, context)) {
                    computer.compute(context, chunkX, chunkZ, merger, tickId);
                }
            } catch (Exception e) {
                LOGGER.warn("Error in {} for chunk {},{}: {}",
                    computer.getName(), chunkX, chunkZ, e.getMessage());
            }
        }
    }

    /**
     * Get pipeline statistics
     */
    public static PipelineStats getStats() {
        return new PipelineStats(
            totalChunksProcessed.get(),
            totalProcessingTime.get(),
            COMPUTERS.size()
        );
    }

    /**
     * Reset statistics
     */
    public static void resetStats() {
        totalChunksProcessed.set(0);
        totalProcessingTime.set(0);
    }

    /**
     * Clear all registered computers
     */
    public static void clear() {
        COMPUTERS.clear();
    }

    public record PipelineResult(
        int chunksProcessed,
        int computersUsed,
        long processingTimeNanos
    ) {
        public double avgTimePerChunkMs() {
            return chunksProcessed > 0 ?
                (processingTimeNanos / chunksProcessed) / 1_000_000.0 : 0;
        }
    }

    public record PipelineStats(
        long totalChunks,
        long totalTimeNanos,
        int registeredComputers
    ) {
        public double avgTimePerChunkMs() {
            return totalChunks > 0 ?
                (totalTimeNanos / totalChunks) / 1_000_000.0 : 0;
        }
    }
}
