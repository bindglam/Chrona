package org.wrd.chrona.chunk.parallel;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wrd.chrona.chunk.WorldGenContext;
import org.wrd.chrona.tick.phase.ChunkIntent;
import org.wrd.chrona.tick.phase.IntentMerger;
import org.wrd.chrona.tick.phase.IntentPriority;

import java.util.Random;

/**
 * Noise-based terrain generation intent computer
 * Generates heightmap and basic terrain data using noise functions
 */
public class NoiseIntentComputer implements ChunkIntentComputer {
    private static final Logger LOGGER = LogManager.getLogger(NoiseIntentComputer.class);

    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int WORLD_HEIGHT = 384;
    private static final int WORLD_MIN_Y = -64;
    private static final int NUM_SECTIONS = WORLD_HEIGHT / SECTION_HEIGHT;

    // Noise parameters
    private static final double SCALE = 0.01;
    private static final double AMPLITUDE = 64.0;
    private static final int BASE_HEIGHT = 64;

    @Override
    public boolean supports(int chunkX, int chunkZ, WorldGenContext context) {
        // Support all chunks for noise generation
        return true;
    }

    @Override
    public void compute(WorldGenContext context, int chunkX, int chunkZ, IntentMerger merger, long tickId) {
        long startTime = System.nanoTime();

        try {
            // Generate heightmap
            int[] heightmap = generateHeightmap(chunkX, chunkZ, context.getSeed());

            // Generate section data (simplified)
            LevelChunkSection[] sections = new LevelChunkSection[0]; // Placeholder

            // Generate biome data (simplified)
            byte[] biomeData = new byte[0]; // Placeholder

            long generationTime = System.nanoTime() - startTime;

            // Create chunk data
            ChunkIntent.ChunkData chunkData = new ChunkIntent.ChunkData(
                sections,
                heightmap,
                biomeData,
                generationTime
            );

            // Submit intent
            merger.submit(
                ChunkIntent.generate(
                    chunkX,
                    chunkZ,
                    chunkData,
                    "noise_generator",
                    IntentPriority.withTimestamp(IntentPriority.VANILLA)
                ),
                tickId
            );

        } catch (Exception e) {
            LOGGER.warn("Error generating noise for chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
        }
    }

    /**
     * Generate heightmap using simplex-like noise
     */
    private int[] generateHeightmap(int chunkX, int chunkZ, long seed) {
        int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
        Random random = new Random(seed ^ ((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL)));

        int worldX = chunkX * CHUNK_SIZE;
        int worldZ = chunkZ * CHUNK_SIZE;

        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                double nx = (worldX + x) * SCALE;
                double nz = (worldZ + z) * SCALE;

                // Simplified noise calculation (would use actual noise in production)
                double noise = simplexNoise(nx, nz, seed);
                int height = (int) (BASE_HEIGHT + noise * AMPLITUDE);

                // Clamp to valid range
                height = Math.max(WORLD_MIN_Y + 1, Math.min(WORLD_MIN_Y + WORLD_HEIGHT - 1, height));

                heightmap[z * CHUNK_SIZE + x] = height;
            }
        }

        return heightmap;
    }

    /**
     * Simplified noise function (placeholder for real implementation)
     */
    private double simplexNoise(double x, double z, long seed) {
        // Simplified noise - in production would use proper Simplex/Perlin
        double combined = x * 0.1 + z * 0.1 + (seed % 1000) * 0.001;
        return Math.sin(combined) * Math.cos(combined * 0.7);
    }

    @Override
    public int getPriority() {
        return 100; // Run noise before structures
    }

    @Override
    public String getName() {
        return "NoiseIntentComputer";
    }
}
