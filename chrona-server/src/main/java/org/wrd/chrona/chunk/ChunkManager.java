package org.wrd.chrona.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ChunkManager - 청크 생성 및 관리
 * Parallel chunk generation using pure function + deterministic commit pattern
 */
public class ChunkManager {
    private static final Logger LOGGER = LogManager.getLogger(ChunkManager.class);

    private final ServerLevel level;
    private final int generationThreads;
    private final int ioThreads;
    private final int lightingThreads;
    private final ParallelChunkGenerator parallelGenerator;

    public ChunkManager(ServerLevel level, int generationThreads, int ioThreads, int lightingThreads) {
        this.level = level;
        this.generationThreads = generationThreads;
        this.ioThreads = ioThreads;
        this.lightingThreads = lightingThreads;
        this.parallelGenerator = new ParallelChunkGenerator(level, generationThreads);

        LOGGER.info("ChunkManager initialized for {} with {} gen threads, {} IO threads, {} lighting threads",
            level.dimension(), generationThreads, ioThreads, lightingThreads);
    }

    /**
     * Generate chunks in batch (parallel computation + deterministic commit)
     */
    public Map<Long, LevelChunk> generateChunkBatch(List<ParallelChunkGenerator.ChunkPos> positions, int radius) {
        if (generationThreads <= 1 || positions.size() < 4) {
            // Fall back to vanilla for small batches
            return Map.of();
        }

        return parallelGenerator.generateBatch(positions, radius);
    }

    /**
     * Generate chunks asynchronously using Paper's chunk system.
     */
    public CompletableFuture<Map<Long, LevelChunk>> generateChunkBatchAsync(List<ParallelChunkGenerator.ChunkPos> positions, int radius) {
        return parallelGenerator.generateBatchAsync(positions, radius);
    }

    /**
     * Generate chunks around a player (typical use case)
     */
    public Map<Long, LevelChunk> generateAroundPlayer(int centerX, int centerZ, int radius) {
        List<ParallelChunkGenerator.ChunkPos> positions = new ArrayList<>();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // Check if chunk needs generation
                if (!level.getChunkSource().hasChunk(x, z)) {
                    positions.add(new ParallelChunkGenerator.ChunkPos(x, z));
                }
            }
        }

        if (positions.isEmpty()) {
            return Map.of();
        }

        return generateChunkBatch(positions, radius);
    }

    public void shutdown() {
        LOGGER.info("ChunkManager shutting down for {}", level.dimension());
        parallelGenerator.shutdown();
    }
}
