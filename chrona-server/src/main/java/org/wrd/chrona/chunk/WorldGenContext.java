package org.wrd.chrona.chunk;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Immutable snapshot of world generation context
 * Captures minimum data needed for deterministic chunk generation
 */
public class WorldGenContext {
    private static final Logger LOGGER = LogManager.getLogger(WorldGenContext.class);

    private final long seed;
    private final String dimensionKey;
    private final NeighborSummary[] neighborSummaries;
    private final long captureTime;

    public WorldGenContext(long seed, String dimensionKey, NeighborSummary[] neighborSummaries) {
        this.seed = seed;
        this.dimensionKey = dimensionKey;
        this.neighborSummaries = neighborSummaries;
        this.captureTime = System.nanoTime();
    }

    public long getSeed() {
        return seed;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }

    public NeighborSummary[] getNeighborSummaries() {
        return neighborSummaries;
    }

    public long getCaptureTime() {
        return captureTime;
    }

    /**
     * Summary of neighboring chunk data (not full chunk)
     */
    public static class NeighborSummary {
        private final int chunkX;
        private final int chunkZ;
        private final Map<Heightmap.Types, int[]> heightmaps;
        private final Holder<Biome>[] biomes; // 4x4x4 sections
        private final boolean hasStructure;

        public NeighborSummary(int chunkX, int chunkZ, ChunkAccess chunk) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.heightmaps = copyHeightmaps(chunk);
            this.biomes = copyBiomes(chunk);
            this.hasStructure = chunk.getAllStarts().values().stream()
                .anyMatch(start -> start != null && start.isValid());
        }

        @SuppressWarnings("unchecked")
        private Map<Heightmap.Types, int[]> copyHeightmaps(ChunkAccess chunk) {
            Map<Heightmap.Types, int[]> copy = new java.util.HashMap<>();
            for (Heightmap.Types type : Heightmap.Types.values()) {
                Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
                if (heightmap != null && heightmap.getRawData() != null) {
                    long[] rawData = heightmap.getRawData();
                    // Convert long[] to int[] for simplified storage
                    int[] intData = new int[rawData.length];
                    for (int i = 0; i < rawData.length; i++) {
                        intData[i] = (int) (rawData[i] & 0xFFFFFFFF);
                    }
                    copy.put(type, intData);
                }
            }
            return copy;
        }

        @SuppressWarnings("unchecked")
        private Holder<Biome>[] copyBiomes(ChunkAccess chunk) {
            try {
                // Sample biomes at key positions (4x4x4 grid = 64 samples)
                int samplesPerAxis = 4;
                int totalSamples = samplesPerAxis * samplesPerAxis * samplesPerAxis;
                Holder<Biome>[] biomes = new Holder[totalSamples];

                int index = 0;
                for (int y = 0; y < samplesPerAxis; y++) {
                    for (int z = 0; z < samplesPerAxis; z++) {
                        for (int x = 0; x < samplesPerAxis; x++) {
                            try {
                                // Sample at evenly distributed points
                                int sampleX = x * 4; // 16 blocks / 4 samples = 4 blocks apart
                                int sampleY = y * 16; // Vertical spacing
                                int sampleZ = z * 4;

                                biomes[index++] = chunk.getNoiseBiome(sampleX >> 2, sampleY >> 2, sampleZ >> 2);
                            } catch (Exception e) {
                                // Skip failed samples
                                biomes[index++] = null;
                            }
                        }
                    }
                }

                return biomes;
            } catch (Exception e) {
                LOGGER.warn("Failed to copy biomes for chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
                return new Holder[0];
            }
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        public Map<Heightmap.Types, int[]> getHeightmaps() {
            return heightmaps;
        }

        public boolean hasStructure() {
            return hasStructure;
        }
    }
}
