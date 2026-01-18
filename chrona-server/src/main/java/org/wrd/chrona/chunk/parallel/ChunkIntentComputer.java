package org.wrd.chrona.chunk.parallel;

import org.wrd.chrona.chunk.WorldGenContext;
import org.wrd.chrona.tick.phase.IntentMerger;

/**
 * Interface for parallel chunk computation
 * Computes chunk generation data and generates ChunkIntents
 */
public interface ChunkIntentComputer {

    /**
     * Check if this computer supports the given chunk
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param context World generation context
     * @return true if this computer should process the chunk
     */
    boolean supports(int chunkX, int chunkZ, WorldGenContext context);

    /**
     * Compute chunk data and generate intents
     * This method must be thread-safe and not modify world state
     *
     * @param context World generation context (read-only)
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param merger Intent merger to submit intents to
     * @param tickId Current tick ID
     */
    void compute(WorldGenContext context, int chunkX, int chunkZ, IntentMerger merger, long tickId);

    /**
     * Get the priority of this computer
     * Higher priority computers run first
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Get a name for this computer (for logging)
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
